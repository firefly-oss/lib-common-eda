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

import java.lang.annotation.*;

/**
 * Annotation to mark methods as event listeners.
 * <p>
 * Methods annotated with this will automatically receive events from the configured
 * messaging system based on the specified filters and destinations.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @EventListener(
 *     destinations = {"user-events", "order-events"},
 *     eventTypes = {"user.created", "user.updated"},
 *     consumerType = PublisherType.KAFKA,
 *     connectionId = "primary"
 * )
 * public Mono<Void> handleUserEvents(EventEnvelope event) {
 *     // handle the event
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {

    /**
     * The destinations to listen on (topics, queues, channels, etc.).
     * <p>
     * If empty, will listen on all configured destinations.
     *
     * @return the destinations
     */
    String[] destinations() default {};

    /**
     * The event types this listener should handle.
     * <p>
     * If empty, the listener will handle all event types.
     * Supports glob patterns (*, ?, etc.) for flexible matching.
     *
     * @return the event types
     */
    String[] eventTypes() default {};

    /**
     * The consumer type to use for this listener.
     *
     * @return the consumer type
     */
    PublisherType consumerType() default PublisherType.AUTO;

    /**
     * The connection ID to use for this listener.
     *
     * @return the connection ID
     */
    String connectionId() default "default";

    /**
     * Error handling strategy for failed message processing.
     *
     * @return the error handling strategy
     */
    ErrorHandlingStrategy errorStrategy() default ErrorHandlingStrategy.LOG_AND_CONTINUE;

    /**
     * Maximum number of retry attempts for failed messages.
     *
     * @return the max retry attempts
     */
    int maxRetries() default 3;

    /**
     * Delay between retry attempts in milliseconds.
     *
     * @return the retry delay
     */
    long retryDelayMs() default 1000;

    /**
     * Whether to automatically acknowledge successfully processed messages.
     *
     * @return true for auto-acknowledgment
     */
    boolean autoAck() default true;

    /**
     * Consumer group ID for this listener (applies to group-based consumers like Kafka).
     *
     * @return the consumer group ID
     */
    String groupId() default "";

    /**
     * Filter expression that must evaluate to true for the event to be processed.
     * <p>
     * Supports SpEL expressions with access to the EventEnvelope.
     * If empty, all matching events will be processed.
     *
     * @return the filter expression
     */
    String condition() default "";

    /**
     * Priority of this listener (higher numbers = higher priority).
     * <p>
     * When multiple listeners match the same event, they are executed in priority order.
     *
     * @return the priority
     */
    int priority() default 0;

    /**
     * Whether this listener should run asynchronously.
     *
     * @return true for async processing
     */
    boolean async() default true;

    /**
     * Timeout for event processing in milliseconds.
     * <p>
     * If 0, no timeout will be applied.
     *
     * @return the timeout in milliseconds
     */
    long timeoutMs() default 0;
}