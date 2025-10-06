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

package com.firefly.common.eda.publisher;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.resilience.ResilientEventPublisherFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for creating and managing event publishers.
 * <p>
 * This factory provides centralized access to different types of event publishers,
 * supports auto-discovery of available publishers, and manages publisher instances
 * with connection-specific configurations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisherFactory {

    private final List<EventPublisher> availablePublishers;
    private final EdaProperties edaProperties;
    private final ObjectProvider<ResilientEventPublisherFactory> resilienceFactoryProvider;
    
    // Cache publishers by type and connection ID
    private final Map<String, EventPublisher> publisherCache = new ConcurrentHashMap<>();
    private Map<String, EventPublisher> publisherMap;

    /**
     * Gets an event publisher for the specified type and connection ID.
     *
     * @param publisherType the publisher type
     * @param connectionId the connection ID (null for default)
     * @return the event publisher or null if not available
     */
    public EventPublisher getPublisher(PublisherType publisherType, String connectionId) {
        if (publisherType == PublisherType.AUTO) {
            return getAutoSelectedPublisher(connectionId);
        }

        String cacheKey = getCacheKey(publisherType, connectionId);
        return publisherCache.computeIfAbsent(cacheKey, key -> createPublisher(publisherType, connectionId));
    }

    /**
     * Gets an event publisher for the specified type using the default connection.
     *
     * @param publisherType the publisher type
     * @return the event publisher or null if not available
     */
    public EventPublisher getPublisher(PublisherType publisherType) {
        return getPublisher(publisherType, null);
    }

    /**
     * Gets an event publisher with a custom default destination that overrides application properties.
     * <p>
     * This method allows dynamic topic/destination selection at runtime. The returned publisher
     * will use the specified destination as its default when no explicit destination is provided
     * to the publish methods.
     *
     * @param publisherType the publisher type
     * @param customDefaultDestination the custom default destination (topic, queue, exchange, etc.)
     * @return the event publisher with custom default destination or null if not available
     */
    public EventPublisher getPublisherWithDestination(PublisherType publisherType, String customDefaultDestination) {
        return getPublisherWithDestination(publisherType, null, customDefaultDestination);
    }

    /**
     * Gets an event publisher with a custom default destination and specific connection ID.
     * <p>
     * This method provides the most flexibility, allowing both connection-specific configuration
     * and dynamic destination override.
     *
     * @param publisherType the publisher type
     * @param connectionId the connection ID (null for default)
     * @param customDefaultDestination the custom default destination (topic, queue, exchange, etc.)
     * @return the event publisher with custom default destination or null if not available
     */
    public EventPublisher getPublisherWithDestination(PublisherType publisherType, String connectionId, String customDefaultDestination) {
        if (customDefaultDestination == null || customDefaultDestination.trim().isEmpty()) {
            log.debug("No custom destination provided, returning standard publisher");
            return getPublisher(publisherType, connectionId);
        }

        // Get the base publisher
        EventPublisher basePublisher = getPublisher(publisherType, connectionId);
        if (basePublisher == null) {
            return null;
        }

        // Wrap with destination-aware publisher
        log.debug("Creating destination-aware publisher: type={}, connectionId={}, customDestination={}",
                 publisherType, connectionId, customDefaultDestination);
        return new DestinationAwarePublisher(basePublisher, customDefaultDestination);
    }

    /**
     * Gets all available publishers with their types.
     *
     * @return map of publisher types to publisher instances
     */
    public Map<String, EventPublisher> getAvailablePublishers() {
        if (publisherMap == null) {
            initPublisherMap();
        }
        return Map.copyOf(publisherMap);
    }

    /**
     * Checks if a specific publisher type is available.
     *
     * @param publisherType the publisher type to check
     * @return true if available
     */
    public boolean isPublisherAvailable(PublisherType publisherType) {
        if (publisherType == PublisherType.AUTO) {
            return getAutoSelectedPublisher(null) != null;
        }
        
        EventPublisher publisher = getPublisher(publisherType);
        return publisher != null && publisher.isAvailable();
    }

    /**
     * Gets health information for all publishers.
     *
     * @return map of publisher types to health information
     */
    public Map<String, PublisherHealth> getPublishersHealth() {
        return availablePublishers.stream()
                .collect(Collectors.toMap(
                        publisher -> publisher.getPublisherType().name().toLowerCase().replace("_", "-"),
                        publisher -> PublisherHealth.builder()
                                .publisherType(publisher.getPublisherType())
                                .available(publisher.isAvailable())
                                .status(publisher.isAvailable() ? "UP" : "DOWN")
                                .build()
                ));
    }

    /**
     * Creates a new publisher instance for the specified type and connection.
     *
     * @param publisherType the publisher type
     * @param connectionId the connection ID
     * @return the publisher instance or null if not available
     */
    private EventPublisher createPublisher(PublisherType publisherType, String connectionId) {
        if (publisherMap == null) {
            initPublisherMap();
        }

        String publisherTypeKey = publisherType.name().toLowerCase().replace("_", "-");
        EventPublisher basePublisher = publisherMap.get(publisherTypeKey);

        if (basePublisher == null) {
            log.warn("Publisher of type {} is not available", publisherType);
            return null;
        }

        if (!basePublisher.isAvailable()) {
            log.warn("Publisher of type {} is not properly configured", publisherType);
            return null;
        }

        // Configure connection if the publisher supports it
        if (basePublisher instanceof ConnectionAwarePublisher connectionAwarePublisher) {
            String connId = connectionId != null ? connectionId : edaProperties.getDefaultConnectionId();
            connectionAwarePublisher.setConnectionId(connId);
            
            if (!connectionAwarePublisher.isConnectionConfigured(connId)) {
                log.warn("Connection '{}' is not configured for publisher type {}", connId, publisherType);
                return null;
            }
        }

        // Apply resilience wrapper if available
        EventPublisher publisher = basePublisher;
        ResilientEventPublisherFactory resilienceFactory = resilienceFactoryProvider.getIfAvailable();
        if (resilienceFactory != null) {
            log.debug("Applying resilience wrapper to publisher: type={}, connectionId={}", publisherType, connectionId);
            publisher = resilienceFactory.createResilientPublisher(
                    basePublisher,
                    publisherType.name().toLowerCase() + "_" + connectionId
            );
        }

        log.debug("Created publisher: type={}, connectionId={}", publisherType, connectionId);
        return publisher;
    }

    /**
     * Automatically selects the best available publisher.
     *
     * @param connectionId the connection ID
     * @return the selected publisher or null if none available
     */
    private EventPublisher getAutoSelectedPublisher(String connectionId) {
        // Priority order: KAFKA → RABBITMQ → APPLICATION_EVENT
        PublisherType[] priorityOrder = {
                PublisherType.KAFKA,
                PublisherType.RABBITMQ,
                PublisherType.APPLICATION_EVENT
        };

        for (PublisherType type : priorityOrder) {
            EventPublisher publisher = createPublisher(type, connectionId);
            if (publisher != null && publisher.isAvailable()) {
                log.info("Auto-selected publisher: type={}, connectionId={}", type, connectionId);
                return publisher;
            }
        }

        log.warn("No available publishers found for auto-selection");
        return null;
    }

    /**
     * Initializes the publisher map from available publishers.
     */
    private void initPublisherMap() {
        log.info("Initializing publisher map with {} publishers", availablePublishers.size());
        
        publisherMap = availablePublishers.stream()
                .collect(Collectors.toMap(
                        publisher -> publisher.getPublisherType().name().toLowerCase().replace("_", "-"),
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("Duplicate publisher type '{}', using first one", existing.getPublisherType());
                            return existing;
                        }
                ));

        log.info("Publisher map initialized with {} entries", publisherMap.size());
        publisherMap.forEach((type, publisher) ->
                log.debug("Available publisher: {} -> {}", type, publisher.getClass().getSimpleName()));
    }

    /**
     * Gets all publishers currently in the cache.
     *
     * @return map of cache keys to publisher instances
     */
    public Map<String, EventPublisher> getAllPublishers() {
        return Map.copyOf(publisherCache);
    }

    /**
     * Gets the default publisher based on configuration.
     *
     * @return the default publisher
     */
    public EventPublisher getDefaultPublisher() {
        PublisherType defaultType = edaProperties.getDefaultPublisherType();
        return getPublisher(defaultType, edaProperties.getDefaultConnectionId());
    }

    /**
     * Gets the default publisher with a custom default destination.
     * <p>
     * This is a convenience method that uses the configured default publisher type
     * and connection ID but overrides the default destination.
     *
     * @param customDefaultDestination the custom default destination
     * @return the default publisher with custom destination
     */
    public EventPublisher getDefaultPublisherWithDestination(String customDefaultDestination) {
        PublisherType defaultType = edaProperties.getDefaultPublisherType();
        return getPublisherWithDestination(defaultType, edaProperties.getDefaultConnectionId(), customDefaultDestination);
    }

    /**
     * Generates a cache key for a publisher type and connection ID.
     *
     * @param publisherType the publisher type
     * @param connectionId the connection ID
     * @return the cache key
     */
    private String getCacheKey(PublisherType publisherType, String connectionId) {
        String connId = connectionId != null ? connectionId : edaProperties.getDefaultConnectionId();
        return publisherType.name() + ":" + connId;
    }
}