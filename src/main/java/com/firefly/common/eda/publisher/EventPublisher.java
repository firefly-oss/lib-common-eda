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
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Unified interface for publishing events to different messaging systems.
 * <p>
 * This interface provides a reactive API for publishing events to various messaging platforms
 * including Kafka, RabbitMQ, and in-memory Spring Events.
 * <p>
 * The interface is designed to be:
 * <ul>
 *   <li>Reactive - Returns Mono&lt;Void&gt; for non-blocking operations</li>
 *   <li>Flexible - Supports multiple messaging platforms through abstractions</li>
 *   <li>Extensible - Allows custom serializers and metadata</li>
 *   <li>Resilient - Integrates with circuit breakers and retry mechanisms</li>
 *   <li>Observable - Provides hooks for metrics and monitoring</li>
 * </ul>
 */
public interface EventPublisher {

    /**
     * Publishes an event to the specified destination.
     *
     * @param event the event object to publish
     * @param destination the destination to publish to (topic, queue, exchange, etc.)
     * @param headers custom headers to include with the message
     * @return a Mono that completes when the event is published
     */
    Mono<Void> publish(Object event, String destination, Map<String, Object> headers);

    /**
     * Publishes an event with simplified parameters.
     *
     * @param event the event object to publish
     * @param destination the destination to publish to
     * @return a Mono that completes when the event is published
     */
    default Mono<Void> publish(Object event, String destination) {
        return publish(event, destination, null);
    }

    /**
     * Checks if this publisher is available and properly configured.
     *
     * @return true if the publisher is ready for use
     */
    boolean isAvailable();

    /**
     * Gets the publisher type identifier.
     *
     * @return the publisher type
     */
    PublisherType getPublisherType();

    /**
     * Gets the default destination for this publisher if none is specified.
     *
     * @return the default destination, or null if none is configured
     */
    default String getDefaultDestination() {
        return null;
    }

    /**
     * Gets health information about this publisher.
     *
     * @return a Mono containing health status
     */
    default Mono<PublisherHealth> getHealth() {
        return Mono.just(PublisherHealth.builder()
                .publisherType(getPublisherType())
                .available(isAvailable())
                .status(isAvailable() ? "UP" : "DOWN")
                .build());
    }

    /**
     * Performs any necessary cleanup when the publisher is no longer needed.
     */
    default void close() {
        // Default implementation does nothing
    }
}