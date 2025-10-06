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
 * Annotation to automatically publish method results as events.
 * <p>
 * When applied to a method, the return value will be automatically published
 * to the configured messaging system after successful method execution.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @PublishResult(
 *     publisherType = PublisherType.KAFKA,
 *     destination = "user-events",
 *     eventType = "user.created",
 *     connectionId = "primary"
 * )
 * public Mono<User> createUser(CreateUserRequest request) {
 *     // method implementation
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublishResult {

    /**
     * The type of publisher to use for publishing the result.
     *
     * @return the publisher type
     */
    PublisherType publisherType() default PublisherType.AUTO;

    /**
     * The destination to publish to (topic, queue, channel, etc.).
     * <p>
     * If empty, the default destination for the publisher type will be used.
     * Supports SpEL expressions with access to method parameters and result.
     *
     * @return the destination
     */
    String destination() default "";

    /**
     * The event type to use when publishing.
     * <p>
     * Used for routing and filtering on the consumer side.
     * Supports SpEL expressions with access to method parameters and result.
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
     * Whether to publish the result asynchronously.
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
     * Whether to publish the result even if the method throws an exception.
     * <p>
     * If true, the exception will be included in the event payload.
     *
     * @return true to publish on error
     */
    boolean publishOnError() default false;

    /**
     * Condition expression that must evaluate to true for the event to be published.
     * <p>
     * Supports SpEL expressions with access to method parameters and result.
     * If empty, the event will always be published (subject to other constraints).
     *
     * @return the condition expression
     */
    String condition() default "";

    /**
     * Key expression for partitioning or routing.
     * <p>
     * Supports SpEL expressions with access to method parameters and result.
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
}