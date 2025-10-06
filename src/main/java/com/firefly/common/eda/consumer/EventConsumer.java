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

package com.firefly.common.eda.consumer;

import com.firefly.common.eda.event.EventEnvelope;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Interface for consuming events from messaging systems.
 * <p>
 * This interface provides a reactive API for consuming events from various messaging platforms
 * including Kafka, RabbitMQ, and in-memory Spring Events.
 */
public interface EventConsumer {

    /**
     * Starts consuming events from the configured destinations.
     * <p>
     * Returns a Flux of EventEnvelope objects representing incoming events.
     * The Flux will continue emitting events until explicitly stopped.
     *
     * @return a Flux of incoming events
     */
    Flux<EventEnvelope> consume();

    /**
     * Starts consuming events from specific destinations.
     *
     * @param destinations the destinations to consume from
     * @return a Flux of incoming events
     */
    Flux<EventEnvelope> consume(String... destinations);

    /**
     * Starts the consumer.
     *
     * @return a Mono that completes when the consumer is started
     */
    Mono<Void> start();

    /**
     * Stops the consumer.
     *
     * @return a Mono that completes when the consumer is stopped
     */
    Mono<Void> stop();

    /**
     * Checks if the consumer is currently running.
     *
     * @return true if the consumer is running
     */
    boolean isRunning();

    /**
     * Gets the consumer type identifier.
     *
     * @return the consumer type
     */
    String getConsumerType();

    /**
     * Checks if this consumer is available and properly configured.
     *
     * @return true if the consumer is ready for use
     */
    boolean isAvailable();

    /**
     * Gets health information about this consumer.
     *
     * @return a Mono containing health status
     */
    default Mono<ConsumerHealth> getHealth() {
        return Mono.just(ConsumerHealth.builder()
                .consumerType(getConsumerType())
                .available(isAvailable())
                .running(isRunning())
                .status(isAvailable() && isRunning() ? "UP" : "DOWN")
                .build());
    }

    /**
     * Performs any necessary cleanup when the consumer is no longer needed.
     */
    default void close() {
        // Default implementation does nothing
    }


}