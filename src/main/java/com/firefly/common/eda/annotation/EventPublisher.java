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
 * Annotation to mark methods for publishing events.
 * <p>
 * This is a generic annotation that can be used to publish events from method parameters
 * or method execution. It provides a unified way to publish events across different
 * messaging platforms.
 * <p>
 * This annotation can be used in two ways:
 * <ol>
 *   <li>On methods to publish method parameters as events (before execution)</li>
 *   <li>Combined with {@link PublishResult} to publish both parameters and results</li>
 * </ol>
 * <p>
 * Example usage for publishing parameters:
 * <pre>
 * {@code
 * @EventPublisher(
 *     publisherType = PublisherType.KAFKA,
 *     destination = "user-commands",
 *     eventType = "user.create.command",
 *     parameterIndex = 0  // Publish first parameter
 * )
 * public Mono<User> createUser(CreateUserCommand command) {
 *     // method implementation
 * }
 * }
 * </pre>
 * <p>
 * Example usage for publishing all parameters:
 * <pre>
 * {@code
 * @EventPublisher(
 *     publisherType = PublisherType.RABBITMQ,
 *     destination = "audit-events",
 *     eventType = "audit.log"
 * )
 * public void auditAction(String userId, String action, Map<String, Object> details) {
 *     // All parameters will be wrapped in an event and published
 * }
 * }
 * </pre>
 * <p>
 * Example usage combined with {@link PublishResult}:
 * <pre>
 * {@code
 * @EventPublisher(
 *     publisherType = PublisherType.KAFKA,
 *     destination = "user-commands",
 *     eventType = "user.create.requested"
 * )
 * @PublishResult(
 *     publisherType = PublisherType.KAFKA,
 *     destination = "user-events",
 *     eventType = "user.created"
 * )
 * public Mono<User> createUser(CreateUserRequest request) {
 *     // Publishes request before execution and result after execution
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventPublisher {

    /**
     * The type of publisher to use for publishing the event.
     *
     * @return the publisher type
     */
    PublisherType publisherType() default PublisherType.AUTO;

    /**
     * The destination to publish to (topic, queue, channel, etc.).
     * <p>
     * If empty, the default destination for the publisher type will be used.
     * Supports SpEL expressions with access to method parameters.
     *
     * @return the destination
     */
    String destination() default "";

    /**
     * The event type to use when publishing.
     * <p>
     * Used for routing and filtering on the consumer side.
     * Supports SpEL expressions with access to method parameters.
     *
     * @return the event type
     */
    String eventType() default "";

    /**
     * The connection ID to use for this publisher.
     * <p>
     * Allows using different configurations for the same publisher type.
     *
     * @return the connection ID
     */
    String connectionId() default "default";

    /**
     * The serializer bean name to use for this publication.
     * <p>
     * If specified, this serializer will be used instead of the default.
     *
     * @return the serializer bean name
     */
    String serializer() default "";

    /**
     * Whether to publish the event asynchronously.
     * <p>
     * If true, the method will not wait for the publication to complete.
     *
     * @return true for async publication
     */
    boolean async() default true;

    /**
     * The timeout in milliseconds for the publication operation.
     * <p>
     * If 0, the default timeout will be used.
     *
     * @return the timeout in milliseconds
     */
    long timeoutMs() default 0;

    /**
     * The index of the parameter to publish.
     * <p>
     * If -1 (default), all parameters will be wrapped in a map and published.
     * If >= 0, only the parameter at that index will be published.
     *
     * @return the parameter index
     */
    int parameterIndex() default -1;

    /**
     * Condition expression that must evaluate to true for the event to be published.
     * <p>
     * Supports SpEL expressions with access to method parameters.
     * If empty, the event will always be published.
     *
     * @return the condition expression
     */
    String condition() default "";

    /**
     * Key expression for partitioning or routing.
     * <p>
     * Supports SpEL expressions with access to method parameters.
     * The evaluated value will be used as the message key for platforms that support it.
     *
     * @return the key expression
     */
    String key() default "";

    /**
     * Custom headers to include with the published event.
     * <p>
     * Format: "key1=value1,key2=value2"
     * Values support SpEL expressions.
     *
     * @return the custom headers
     */
    String[] headers() default {};

    /**
     * When to publish the event relative to method execution.
     *
     * @return the publish timing
     */
    PublishTiming timing() default PublishTiming.BEFORE;

    /**
     * Enum defining when to publish the event.
     */
    enum PublishTiming {
        /**
         * Publish before method execution.
         */
        BEFORE,

        /**
         * Publish after method execution (regardless of success or failure).
         */
        AFTER,

        /**
         * Publish both before and after method execution.
         */
        BOTH
    }
}

