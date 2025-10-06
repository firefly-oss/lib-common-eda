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

package com.firefly.common.eda.integration;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.TestEventModels.SimpleTestEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for resilience patterns in the EDA library.
 * <p>
 * Tests circuit breaker, retry, and rate limiting functionality
 * in real Spring Boot application context.
 */
@DisplayName("Resilience Integration Tests")
class ResilienceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventPublisherFactory publisherFactory;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;



    @Test
    @DisplayName("Should apply circuit breaker to event publishing")
    void shouldApplyCircuitBreakerToEventPublishing() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());

        // Act & Assert - Should complete successfully
        StepVerifier.create(publisher.publish(event, "test-topic", Map.of()))
                .verifyComplete();

        // Verify circuit breaker was created with correct naming pattern
        String expectedCircuitBreakerName = "eda-publisher-application_event_null";
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(expectedCircuitBreakerName)
                .orElse(null);
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Should apply retry to event publishing")
    void shouldApplyRetryToEventPublishing() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act & Assert - Should complete successfully
        StepVerifier.create(publisher.publish(event, "test-topic", Map.of()))
                .verifyComplete();

        // Verify retry was created with correct naming pattern
        String expectedRetryName = "eda-publisher-application_event_null";
        Retry retry = retryRegistry.find(expectedRetryName)
                .orElse(null);
        assertThat(retry).isNotNull();
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should handle publisher health checks with resilience")
    void shouldHandlePublisherHealthChecksWithResilience() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        
        // Act
        boolean isAvailable = publisher.isAvailable();
        
        // Assert
        assertThat(isAvailable).isTrue();
    }

    @Test
    @DisplayName("Should handle multiple concurrent publishing with resilience")
    void shouldHandleMultipleConcurrentPublishingWithResilience() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event1 = new SimpleTestEvent("test-id-1", "test message 1", Instant.now());
        SimpleTestEvent event2 = new SimpleTestEvent("test-id-2", "test message 2", Instant.now());
        SimpleTestEvent event3 = new SimpleTestEvent("test-id-3", "test message 3", Instant.now());
        
        // Act & Assert - All should complete successfully
        StepVerifier.create(
                publisher.publish(event1, "test-topic-1", Map.of())
                    .and(publisher.publish(event2, "test-topic-2", Map.of()))
                    .and(publisher.publish(event3, "test-topic-3", Map.of()))
        ).verifyComplete();
    }

    @Test
    @DisplayName("Should maintain circuit breaker state across multiple calls")
    void shouldMaintainCircuitBreakerStateAcrossMultipleCalls() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act - Make multiple successful calls
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(publisher.publish(event, "test-topic-" + i, Map.of()))
                    .verifyComplete();
        }
        
        // Assert - Circuit breaker should remain closed
        String expectedCircuitBreakerName = "eda-publisher-application_event_null";
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(expectedCircuitBreakerName)
                .orElse(null);
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Should handle publisher with custom connection ID resilience")
    void shouldHandlePublisherWithCustomConnectionIdResilience() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT, "custom-connection");
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, "test-topic", Map.of()))
                .verifyComplete();

        // Verify separate circuit breaker was created for custom connection
        String expectedCircuitBreakerName = "eda-publisher-application_event_custom-connection";
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(expectedCircuitBreakerName)
                .orElse(null);
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Should handle resilience with dynamic destinations")
    void shouldHandleResilienceWithDynamicDestinations() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(PublisherType.APPLICATION_EVENT, "custom-destination");
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, null, Map.of()))
                .verifyComplete();
        
        // Verify publisher is available
        assertThat(publisher.isAvailable()).isTrue();
        assertThat(publisher.getDefaultDestination()).isEqualTo("custom-destination");
    }

    @Test
    @DisplayName("Should handle timeout scenarios gracefully")
    void shouldHandleTimeoutScenariosGracefully() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act & Assert - Should complete within reasonable time
        StepVerifier.create(publisher.publish(event, "test-topic", Map.of()))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should provide resilience metrics")
    void shouldProvideResilienceMetrics() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act - Publish some events
        StepVerifier.create(publisher.publish(event, "test-topic", Map.of()))
                .verifyComplete();
        
        // Assert - Check metrics are available
        String expectedCircuitBreakerName = "eda-publisher-application_event_null";
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(expectedCircuitBreakerName)
                .orElse(null);
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isGreaterThan(0);

        String expectedRetryName = "eda-publisher-application_event_null";
        Retry retry = retryRegistry.find(expectedRetryName)
                .orElse(null);
        assertThat(retry).isNotNull();
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle publisher factory resilience configuration")
    void shouldHandlePublisherFactoryResilienceConfiguration() {
        // Arrange - Request a publisher to populate the cache
        EventPublisher testPublisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        assertThat(testPublisher).isNotNull();

        // Act
        Map<String, EventPublisher> allPublishers = publisherFactory.getAllPublishers();



        // Assert - All publishers should have resilience applied
        assertThat(allPublishers).isNotEmpty();

        for (Map.Entry<String, EventPublisher> entry : allPublishers.entrySet()) {
            String publisherKey = entry.getKey();
            EventPublisher publisher = entry.getValue();

            assertThat(publisher).isNotNull();
            assertThat(publisher.isAvailable()).isTrue();

            // Verify circuit breaker exists for this publisher
            // Convert cache key format (APPLICATION_EVENT:default) to circuit breaker name format (application_event_null)
            String[] parts = publisherKey.split(":");
            String publisherType = parts[0].toLowerCase().replace("_", "_");
            String connectionId = parts.length > 1 && !parts[1].equals("default") ? parts[1] : "null";
            String circuitBreakerName = "eda-publisher-" + publisherType + "_" + connectionId;
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(circuitBreakerName)
                    .orElse(null);
            assertThat(circuitBreaker).isNotNull();
        }
    }

    @Test
    @DisplayName("Should handle auto-selected publisher with resilience")
    void shouldHandleAutoSelectedPublisherWithResilience() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.AUTO);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, "test-topic", Map.of()))
                .verifyComplete();
        
        // Verify publisher is available and has resilience
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should handle resilience with headers and metadata")
    void shouldHandleResilienceWithHeadersAndMetadata() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        Map<String, Object> headers = Map.of(
                "correlation-id", "test-correlation-123",
                "source", "resilience-test",
                "priority", "high"
        );
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, "test-topic", headers))
                .verifyComplete();
        
        // Verify resilience metrics include this call
        String expectedCircuitBreakerName = "eda-publisher-application_event_null";
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(expectedCircuitBreakerName)
                .orElse(null);
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isGreaterThan(0);
    }
}
