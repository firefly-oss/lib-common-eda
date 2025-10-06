/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.eda.resilience;

import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.testconfig.TestEventModels.SimpleTestEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for resilience patterns in event publishing.
 * <p>
 * Tests circuit breaker, retry, and rate limiting functionality
 * to ensure proper error handling and system protection.
 */
@ExtendWith(MockitoExtension.class)
class ResiliencePatternTest {

    @Mock
    private EventPublisher delegatePublisher;

    private SimpleTestEvent testEvent;
    private Map<String, Object> testHeaders;

    @BeforeEach
    void setUp() {
        testEvent = new SimpleTestEvent("test-id", "test message", Instant.now());
        testHeaders = Map.of("correlation-id", "test-corr-id");
    }

    @Test
    void shouldRetryOnFailureAndSucceedEventually() {
        // Arrange
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .build();

        Retry retry = Retry.of("test-retry", retryConfig);

        AtomicInteger attemptCount = new AtomicInteger(0);
        when(delegatePublisher.publish(eq(testEvent), eq("test-topic"), eq(testHeaders)))
            .thenAnswer(invocation -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    return Mono.error(new RuntimeException("Temporary failure"));
                }
                return Mono.empty(); // Success on third attempt
            });

        // Act & Assert
        Mono<Void> retryMono = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RetryOperator.of(retry));

        StepVerifier.create(retryMono)
            .verifyComplete();

        verify(delegatePublisher, times(3))
            .publish(testEvent, "test-topic", testHeaders);
    }

    @Test
    void shouldFailAfterMaxRetryAttempts() {
        // Arrange
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(10))
            .build();

        Retry retry = Retry.of("test-retry", retryConfig);

        when(delegatePublisher.publish(eq(testEvent), eq("test-topic"), eq(testHeaders)))
            .thenReturn(Mono.error(new RuntimeException("Persistent failure")));

        // Act & Assert
        Mono<Void> retryMono = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RetryOperator.of(retry));

        StepVerifier.create(retryMono)
            .expectError(RuntimeException.class)
            .verify();

        verify(delegatePublisher, times(2))
            .publish(testEvent, "test-topic", testHeaders);
    }

    @Test
    void shouldOpenCircuitBreakerAfterFailureThreshold() {
        // Arrange
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .minimumNumberOfCalls(2)
            .slidingWindowSize(4)
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", circuitBreakerConfig);

        when(delegatePublisher.publish(eq(testEvent), eq("test-topic"), eq(testHeaders)))
            .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        // Act - Trigger failures to open circuit breaker
        Mono<Void> circuitBreakerMono1 = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));

        Mono<Void> circuitBreakerMono2 = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));

        StepVerifier.create(circuitBreakerMono1)
            .expectError(RuntimeException.class)
            .verify();

        StepVerifier.create(circuitBreakerMono2)
            .expectError(RuntimeException.class)
            .verify();

        // Assert - Circuit breaker should be open
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Further calls should fail fast without calling delegate
        Mono<Void> circuitBreakerMono3 = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));

        StepVerifier.create(circuitBreakerMono3)
            .expectError()
            .verify();

        // Should only call delegate twice (before circuit opened)
        verify(delegatePublisher, times(2))
            .publish(testEvent, "test-topic", testHeaders);
    }

    @Test
    void shouldRateLimitRequests() {
        // Arrange
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(100))
            .build();

        RateLimiter rateLimiter = RateLimiter.of("test-rate-limiter", rateLimiterConfig);

        when(delegatePublisher.publish(eq(testEvent), eq("test-topic"), eq(testHeaders)))
            .thenReturn(Mono.empty());

        // Act - First two requests should succeed
        Mono<Void> rateLimitedMono1 = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RateLimiterOperator.of(rateLimiter));

        Mono<Void> rateLimitedMono2 = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RateLimiterOperator.of(rateLimiter));

        StepVerifier.create(rateLimitedMono1)
            .verifyComplete();

        StepVerifier.create(rateLimitedMono2)
            .verifyComplete();

        // Third request should be rate limited
        Mono<Void> rateLimitedMono3 = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RateLimiterOperator.of(rateLimiter));

        StepVerifier.create(rateLimitedMono3)
            .expectError()
            .verify();

        // Should only call delegate twice (before rate limit hit)
        verify(delegatePublisher, times(2))
            .publish(testEvent, "test-topic", testHeaders);
    }

    @Test
    void shouldCombineAllResiliencePatterns() {
        // Arrange
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(10))
            .build();

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .minimumNumberOfCalls(1)
            .slidingWindowSize(2)
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .build();

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(100))
            .build();

        Retry retry = Retry.of("test-retry", retryConfig);
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-circuit-breaker", circuitBreakerConfig);
        RateLimiter rateLimiter = RateLimiter.of("test-rate-limiter", rateLimiterConfig);

        when(delegatePublisher.publish(eq(testEvent), eq("test-topic"), eq(testHeaders)))
            .thenReturn(Mono.empty());

        // Act & Assert - Should work with all patterns combined
        Mono<Void> combinedMono = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RateLimiterOperator.of(rateLimiter));

        StepVerifier.create(combinedMono)
            .verifyComplete();

        verify(delegatePublisher).publish(testEvent, "test-topic", testHeaders);
    }

    @Test
    void shouldRetryOnSpecificExceptionsOnly() {
        // Arrange
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .retryExceptions(RuntimeException.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();

        Retry retry = Retry.of("test-retry", retryConfig);

        when(delegatePublisher.publish(eq(testEvent), eq("test-topic"), eq(testHeaders)))
            .thenReturn(Mono.error(new IllegalArgumentException("Should not retry")));

        // Act & Assert - Should not retry on IllegalArgumentException
        Mono<Void> retryMono = Mono.fromSupplier(() -> delegatePublisher.publish(testEvent, "test-topic", testHeaders))
            .flatMap(mono -> mono)
            .transformDeferred(RetryOperator.of(retry));

        StepVerifier.create(retryMono)
            .expectError(IllegalArgumentException.class)
            .verify();

        // Should only call once (no retries)
        verify(delegatePublisher, times(1))
            .publish(testEvent, "test-topic", testHeaders);
    }
}
