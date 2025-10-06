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

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.PublisherHealth;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Resilient wrapper for event publishers that adds circuit breaking, retry, and rate limiting.
 * <p>
 * This decorator enhances any EventPublisher with resilience patterns:
 * <ul>
 *   <li>Circuit Breaker - Prevents cascading failures by failing fast when downstream is unhealthy</li>
 *   <li>Retry - Automatically retries failed operations with configurable backoff</li>
 *   <li>Rate Limiting - Controls the rate of requests to prevent overwhelming downstream systems</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public class ResilientEventPublisher implements EventPublisher {

    private final EventPublisher delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;
    private final EdaProperties.Resilience resilienceConfig;

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        return applyResilience(delegate.publish(event, destination, headers), 
                              destination, event != null ? event.getClass().getSimpleName() : "null", 
                              headers != null ? (String) headers.get("transaction-id") : null);
    }
    
    @Override
    public Mono<Void> publish(Object event, String destination) {
        return publish(event, destination, null);
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable() && 
               (circuitBreaker == null || circuitBreaker.getState() != CircuitBreaker.State.OPEN);
    }

    @Override
    public PublisherType getPublisherType() {
        return delegate.getPublisherType();
    }

    @Override
    public String getDefaultDestination() {
        return delegate.getDefaultDestination();
    }

    @Override
    public Mono<PublisherHealth> getHealth() {
        return delegate.getHealth()
                .map(health -> {
                    Map<String, Object> details = health.getDetails() != null ? 
                            Map.copyOf(health.getDetails()) : Map.of();
                    
                    // Add resilience information
                    if (circuitBreaker != null) {
                        details = addToMap(details, "circuitBreaker.state", circuitBreaker.getState().toString());
                        details = addToMap(details, "circuitBreaker.failureRate", circuitBreaker.getMetrics().getFailureRate());
                        details = addToMap(details, "circuitBreaker.slowCallRate", circuitBreaker.getMetrics().getSlowCallRate());
                    }
                    
                    if (rateLimiter != null) {
                        details = addToMap(details, "rateLimiter.availablePermissions", rateLimiter.getMetrics().getAvailablePermissions());
                        details = addToMap(details, "rateLimiter.waitingThreads", rateLimiter.getMetrics().getNumberOfWaitingThreads());
                    }
                    
                    return PublisherHealth.builder()
                            .publisherType(getPublisherType())
                            .available(isAvailable())
                            .status(isAvailable() ? "UP" : "DOWN")
                            .connectionId(health.getConnectionId())
                            .lastChecked(Instant.now())
                            .details(details)
                            .build();
                });
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * Applies resilience patterns to the given Mono operation.
     */
    private Mono<Void> applyResilience(Mono<Void> operation, String destination, String eventType, String transactionId) {
        Mono<Void> resilientOperation = operation;

        // Apply rate limiting if configured
        if (resilienceConfig.getRateLimiter().isEnabled() && rateLimiter != null) {
            resilientOperation = resilientOperation
                    .transformDeferred(RateLimiterOperator.of(rateLimiter))
                    .doOnError(ex -> log.debug("Rate limiter rejected publish operation: destination={}, type={}", 
                                              destination, eventType, ex));
        }

        // Apply circuit breaker if configured
        if (resilienceConfig.getCircuitBreaker().isEnabled() && circuitBreaker != null) {
            resilientOperation = resilientOperation
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .doOnError(ex -> log.debug("Circuit breaker blocked publish operation: destination={}, type={}", 
                                              destination, eventType, ex));
        }

        // Apply retry if configured
        if (resilienceConfig.getRetry().isEnabled() && retry != null) {
            resilientOperation = resilientOperation
                    .transformDeferred(RetryOperator.of(retry))
                    .doOnError(ex -> log.debug("Retry exhausted for publish operation: destination={}, type={}", 
                                              destination, eventType, ex));
        }

        return resilientOperation
                .doOnSuccess(v -> log.trace("Successfully published event with resilience: destination={}, type={}, transactionId={}", 
                                           destination, eventType, transactionId))
                .doOnError(error -> log.warn("Failed to publish event after applying resilience patterns: destination={}, type={}, transactionId={}, error={}", 
                                            destination, eventType, transactionId, error.getMessage()));
    }

    /**
     * Helper method to add entries to an immutable map.
     */
    private Map<String, Object> addToMap(Map<String, Object> original, String key, Object value) {
        Map<String, Object> mutable = new java.util.HashMap<>(original);
        mutable.put(key, value);
        return Map.copyOf(mutable);
    }
}