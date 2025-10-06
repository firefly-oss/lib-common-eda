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

package com.firefly.common.eda.annotation;

/**
 * Enumeration of supported publisher types.
 * <p>
 * Each type corresponds to a specific messaging platform or protocol.
 * The AUTO type allows the system to automatically select the best
 * available publisher based on configuration and classpath presence.
 */
public enum PublisherType {
    
    /**
     * Automatically select the best available publisher.
     * <p>
     * Selection priority: KAFKA → RABBITMQ → APPLICATION_EVENT
     */
    AUTO,

    /**
     * Spring Application Event Bus (in-memory).
     * <p>
     * Events are published synchronously within the same JVM.
     * Suitable for single-instance applications and testing.
     */
    APPLICATION_EVENT,

    /**
     * Apache Kafka.
     * <p>
     * High-throughput distributed streaming platform.
     * Excellent for event sourcing and real-time data processing.
     */
    KAFKA,

    /**
     * RabbitMQ AMQP Broker.
     * <p>
     * Feature-rich message broker with flexible routing.
     * Great for complex messaging patterns and guaranteed delivery.
     */
    RABBITMQ,

    /**
     * No-operation publisher that discards all messages.
     * <p>
     * Used for testing or when messaging is disabled.
     * All publish operations complete successfully but do nothing.
     */
    NOOP;
    
    /**
     * Gets a human-readable description of the publisher type.
     *
     * @return the description
     */
    public String getDescription() {
        return switch (this) {
            case AUTO -> "Automatic publisher selection";
            case APPLICATION_EVENT -> "Spring Application Events";
            case KAFKA -> "Apache Kafka";
            case RABBITMQ -> "RabbitMQ";
            case NOOP -> "No-operation (disabled)";
        };
    }

    /**
     * Checks if this publisher type supports persistent messaging.
     *
     * @return true if messages can be persisted
     */
    public boolean supportsPersistence() {
        return switch (this) {
            case KAFKA, RABBITMQ -> true;
            case APPLICATION_EVENT, NOOP, AUTO -> false;
        };
    }

    /**
     * Checks if this publisher type supports message ordering.
     *
     * @return true if message ordering is supported
     */
    public boolean supportsOrdering() {
        return switch (this) {
            case KAFKA -> true;
            case RABBITMQ, APPLICATION_EVENT, NOOP, AUTO -> false;
        };
    }

    /**
     * Checks if this publisher type is a cloud-managed service.
     *
     * @return true if it's a cloud service
     */
    public boolean isCloudService() {
        return false; // Kafka and RabbitMQ are self-hosted
    }
}