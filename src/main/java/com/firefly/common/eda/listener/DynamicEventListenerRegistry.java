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

package com.firefly.common.eda.listener;

import com.firefly.common.eda.annotation.PublisherType;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Registry for dynamically registering event listeners at runtime.
 * <p>
 * This allows applications to register event listeners programmatically
 * (e.g., from database configuration) rather than only using @EventListener annotations.
 * <p>
 * Example usage:
 * <pre>
 * registry.registerListener(
 *     "subscription-123",
 *     "user-events",
 *     PublisherType.KAFKA,
 *     (event, headers) -> {
 *         // Process event
 *         return Mono.empty();
 *     }
 * );
 * </pre>
 */
public interface DynamicEventListenerRegistry {

    /**
     * Registers a new dynamic event listener.
     *
     * @param listenerId unique identifier for this listener
     * @param destination the topic/queue/channel to listen on
     * @param consumerType the type of consumer (KAFKA, RABBITMQ, etc.)
     * @param handler the event handler function
     */
    void registerListener(
            String listenerId,
            String destination,
            PublisherType consumerType,
            BiFunction<Object, Map<String, Object>, Mono<Void>> handler
    );

    /**
     * Registers a new dynamic event listener with event type filtering.
     *
     * @param listenerId unique identifier for this listener
     * @param destination the topic/queue/channel to listen on
     * @param eventTypes array of event type patterns to match
     * @param consumerType the type of consumer (KAFKA, RABBITMQ, etc.)
     * @param handler the event handler function
     */
    void registerListener(
            String listenerId,
            String destination,
            String[] eventTypes,
            PublisherType consumerType,
            BiFunction<Object, Map<String, Object>, Mono<Void>> handler
    );

    /**
     * Unregisters a previously registered dynamic listener.
     *
     * @param listenerId the identifier of the listener to remove
     */
    void unregisterListener(String listenerId);

    /**
     * Checks if a listener with the given ID is registered.
     *
     * @param listenerId the listener identifier
     * @return true if registered, false otherwise
     */
    boolean isListenerRegistered(String listenerId);

    /**
     * Gets all registered dynamic listener IDs.
     *
     * @return array of listener IDs
     */
    String[] getRegisteredListenerIds();
}
