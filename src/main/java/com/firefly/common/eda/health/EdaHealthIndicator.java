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

package com.firefly.common.eda.health;

import com.firefly.common.eda.consumer.EventConsumer;
import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eda.publisher.PublisherHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health indicator for the EDA library that reports the status of publishers and consumers.
 * <p>
 * This indicator provides comprehensive health information about:
 * <ul>
 *   <li>EDA library configuration and availability</li>
 *   <li>All registered event publishers and their connection status</li>
 *   <li>All registered event consumers and their running status</li>
 *   <li>Overall system health based on component availability</li>
 * </ul>
 */
@Component
@ConditionalOnClass(name = "org.springframework.boot.actuator.health.HealthIndicator")
@ConditionalOnProperty(prefix = "firefly.eda", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EdaHealthIndicator {

    private final EdaProperties edaProperties;
    private final EventPublisherFactory publisherFactory;
    private final List<EventConsumer> eventConsumers;

    public Map<String, Object> health() {
        try {
            log.debug("Checking EDA health status");
            
            Map<String, Object> healthBuilder = new HashMap<>();
            healthBuilder.put("status", "UP");
            Map<String, Object> details = new HashMap<>();

            // Check overall EDA configuration
            details.put("enabled", edaProperties.isEnabled());
            details.put("defaultPublisherType", edaProperties.getDefaultPublisherType().name());
            details.put("metricsEnabled", edaProperties.isMetricsEnabled());
            details.put("tracingEnabled", edaProperties.isTracingEnabled());

            if (!edaProperties.isEnabled()) {
                healthBuilder.put("status", "DOWN");
                details.put("message", "EDA library is disabled");
                healthBuilder.putAll(details);
                return healthBuilder;
            }

            // Check publishers health
            Map<String, Object> publishersHealth = checkPublishersHealth();
            details.put("publishers", publishersHealth);

            // Check consumers health
            Map<String, Object> consumersHealth = checkConsumersHealth();
            details.put("consumers", consumersHealth);

            // Check resilience configuration
            details.put("resilience", Map.of(
                    "enabled", edaProperties.getResilience().isEnabled(),
                    "circuitBreakerEnabled", edaProperties.getResilience().getCircuitBreaker().isEnabled(),
                    "retryEnabled", edaProperties.getResilience().getRetry().isEnabled(),
                    "rateLimiterEnabled", edaProperties.getResilience().getRateLimiter().isEnabled()
            ));

            // Step events configuration removed - not part of core EDA library

            // Determine overall health status
            boolean hasHealthyPublishers = isHealthyMap(publishersHealth);
            boolean hasHealthyConsumers = consumersHealth.isEmpty() || isHealthyMap(consumersHealth);

            if (hasHealthyPublishers && hasHealthyConsumers) {
                healthBuilder.put("status", "UP");
                details.put("message", "All EDA components are healthy");
            } else {
                healthBuilder.put("status", "DOWN");
                details.put("message", "Some EDA components are unhealthy");
            }
            healthBuilder.putAll(details);
            return healthBuilder;

        } catch (Exception e) {
            log.error("Error checking EDA health: {}", e.getMessage(), e);
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("message", "Error checking EDA health");
            errorHealth.put("error", e.getMessage());
            return errorHealth;
        }
    }

    /**
     * Checks the health of all publishers.
     */
    private Map<String, Object> checkPublishersHealth() {
        try {
            Map<String, PublisherHealth> publishersHealth = publisherFactory.getPublishersHealth();
            
            Map<String, Object> result = Flux.fromIterable(publishersHealth.entrySet())
                    .collectMap(
                            Map.Entry::getKey,
                            entry -> (Object) (entry.getValue() != null ? 
                                    convertPublisherHealthToMap(entry.getValue()) : 
                                    Map.of("status", "UNKNOWN"))
                    )
                    .timeout(Duration.ofSeconds(5))
                    .onErrorReturn(Map.of("error", (Object) "Timeout checking publishers health"))
                    .block();
            
            return result != null ? result : Map.of("error", "No publishers health data");

        } catch (Exception e) {
            log.warn("Error checking publishers health: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Checks the health of all consumers.
     */
    private Map<String, Object> checkConsumersHealth() {
        if (eventConsumers.isEmpty()) {
            return Map.of();
        }

        try {
            Map<String, Object> result = Flux.fromIterable(eventConsumers)
                    .flatMap(consumer -> 
                            consumer.getHealth()
                                    .map(health -> Map.entry(consumer.getConsumerType(), (Object) convertConsumerHealthToMap(health)))
                                    .onErrorReturn(Map.entry(consumer.getConsumerType(), (Object) Map.of("status", "ERROR")))
                    )
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorReturn(Map.of("error", (Object) "Timeout checking consumers health"))
                    .block();
            
            return result != null ? result : Map.of("error", "No consumers health data");

        } catch (Exception e) {
            log.warn("Error checking consumers health: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Converts PublisherHealth to a map for JSON serialization.
     */
    private Map<String, Object> convertPublisherHealthToMap(PublisherHealth health) {
        Map<String, Object> healthMap = new HashMap<>();
        healthMap.put("status", health.getStatus());
        healthMap.put("available", health.isAvailable());
        healthMap.put("publisherType", health.getPublisherType());
        healthMap.put("connectionId", health.getConnectionId());
        healthMap.put("lastChecked", health.getLastChecked());
        
        if (health.getDetails() != null) {
            healthMap.put("details", health.getDetails());
        }
        if (health.getErrorMessage() != null) {
            healthMap.put("error", health.getErrorMessage());
        }
        
        return healthMap;
    }

    /**
     * Converts ConsumerHealth to a map for JSON serialization.
     */
    private Map<String, Object> convertConsumerHealthToMap(com.firefly.common.eda.consumer.ConsumerHealth health) {
        Map<String, Object> healthMap = new HashMap<>();
        healthMap.put("status", health.getStatus());
        healthMap.put("available", health.isAvailable());
        healthMap.put("running", health.isRunning());
        healthMap.put("consumerType", health.getConsumerType());
        healthMap.put("lastChecked", health.getLastChecked());
        
        if (health.getDetails() != null) {
            healthMap.put("details", health.getDetails());
        }
        if (health.getErrorMessage() != null) {
            healthMap.put("error", health.getErrorMessage());
        }
        if (health.getMessagesConsumed() != null) {
            healthMap.put("messagesConsumed", health.getMessagesConsumed());
        }
        if (health.getMessagesProcessed() != null) {
            healthMap.put("messagesProcessed", health.getMessagesProcessed());
        }
        if (health.getMessagesFailures() != null) {
            healthMap.put("messagesFailures", health.getMessagesFailures());
        }
        
        return healthMap;
    }

    /**
     * Checks if any component in the health map is healthy.
     */
    private boolean isHealthyMap(Map<String, Object> healthMap) {
        if (healthMap.isEmpty()) {
            return true; // Empty is considered healthy
        }
        
        return healthMap.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(componentHealth -> {
                    Object status = componentHealth.get("status");
                    return status != null && status.toString().equals("UP");
                });
    }
}