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

import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.publisher.EventPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Factory for creating resilient event publishers with circuit breaker, retry, and rate limiting.
 */
@Component
@ConditionalOnClass({CircuitBreaker.class, Retry.class, RateLimiter.class})
@ConditionalOnProperty(prefix = "firefly.eda.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ResilientEventPublisherFactory {

    private final EdaProperties edaProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Creates a resilient wrapper around the given publisher.
     *
     * @param publisher the base publisher to wrap
     * @param name a unique name for this publisher instance (used for metrics)
     * @return the resilient publisher
     */
    public EventPublisher createResilientPublisher(EventPublisher publisher, String name) {
        EdaProperties.Resilience config = edaProperties.getResilience();
        
        if (!config.isEnabled()) {
            log.debug("Resilience is disabled, returning unwrapped publisher: {}", name);
            return publisher;
        }

        log.debug("Creating resilient publisher: {}", name);

        CircuitBreaker circuitBreaker = null;
        Retry retry = null;
        RateLimiter rateLimiter = null;

        // Create circuit breaker if enabled
        if (config.getCircuitBreaker().isEnabled()) {
            circuitBreaker = createCircuitBreaker(name, config.getCircuitBreaker());
            log.debug("Created circuit breaker for publisher: {}", name);
        }

        // Create retry if enabled
        if (config.getRetry().isEnabled()) {
            retry = createRetry(name, config.getRetry());
            log.debug("Created retry for publisher: {}", name);
        }

        // Create rate limiter if enabled
        if (config.getRateLimiter().isEnabled()) {
            rateLimiter = createRateLimiter(name, config.getRateLimiter());
            log.debug("Created rate limiter for publisher: {}", name);
        }

        return new ResilientEventPublisher(publisher, circuitBreaker, retry, rateLimiter, config);
    }

    /**
     * Creates a circuit breaker with the specified configuration.
     */
    private CircuitBreaker createCircuitBreaker(String name, EdaProperties.Resilience.CircuitBreaker config) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.getFailureRateThreshold())
                .slowCallRateThreshold(config.getSlowCallRateThreshold())
                .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                .slidingWindowSize(config.getSlidingWindowSize())
                .waitDurationInOpenState(config.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
                .build();

        String circuitBreakerName = "eda-publisher-" + name;
        return circuitBreakerRegistry.circuitBreaker(circuitBreakerName, circuitBreakerConfig);
    }

    /**
     * Creates a retry with the specified configuration.
     */
    private Retry createRetry(String name, EdaProperties.Resilience.Retry config) {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(config.getMaxAttempts())
                .waitDuration(config.getWaitDuration())
                .retryExceptions(Exception.class) // Retry on all exceptions
                .build();

        String retryName = "eda-publisher-" + name;
        return retryRegistry.retry(retryName, retryConfig);
    }

    /**
     * Creates a rate limiter with the specified configuration.
     */
    private RateLimiter createRateLimiter(String name, EdaProperties.Resilience.RateLimiter config) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(config.getLimitForPeriod())
                .limitRefreshPeriod(config.getLimitRefreshPeriod())
                .timeoutDuration(config.getTimeoutDuration())
                .build();

        String rateLimiterName = "eda-publisher-" + name;
        return rateLimiterRegistry.rateLimiter(rateLimiterName, rateLimiterConfig);
    }
}