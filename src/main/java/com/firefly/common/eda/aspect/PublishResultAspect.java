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

package com.firefly.common.eda.aspect;

import com.firefly.common.eda.annotation.PublishResult;
import com.firefly.common.eda.expression.SpelExpressionEvaluator;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect that intercepts methods annotated with @PublishResult and automatically
 * publishes the method result as an event.
 * <p>
 * This aspect supports:
 * <ul>
 *   <li>Reactive return types (Mono, Flux)</li>
 *   <li>Non-reactive return types</li>
 *   <li>SpEL expressions in destination, condition, key, and headers</li>
 *   <li>Conditional publishing based on result</li>
 *   <li>Custom headers with SpEL evaluation</li>
 *   <li>Async and sync publishing</li>
 *   <li>Publishing on error (optional)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @PublishResult(
 *     destination = "#{#result.tenantId}-user-events",
 *     eventType = "user.created",
 *     condition = "#result != null && #result.isActive()",
 *     key = "#{#result.userId}",
 *     headers = {"source=user-service", "priority=high"}
 * )
 * public Mono<User> createUser(CreateUserRequest request) {
 *     return userRepository.save(new User(request));
 * }
 * }
 * </pre>
 */
@Aspect
@Component
@Order(100) // Execute after transaction aspects
@RequiredArgsConstructor
@Slf4j
public class PublishResultAspect {

    private final EventPublisherFactory publisherFactory;
    private final SpelExpressionEvaluator spelEvaluator;

    /**
     * Intercepts methods annotated with @PublishResult.
     */
    @Around("@annotation(publishResult)")
    public Object publishResult(ProceedingJoinPoint joinPoint, PublishResult publishResult) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        log.debug("Intercepting method {} with @PublishResult", method.getName());

        // Proceed with method execution
        Object result;
        Throwable executionError = null;
        
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            executionError = throwable;
            
            // If publishOnError is false, rethrow immediately
            if (!publishResult.publishOnError()) {
                throw throwable;
            }
            
            // Otherwise, we'll publish the error and then rethrow
            result = null;
        }

        // Handle reactive return types
        if (result instanceof Mono) {
            return handleMonoResult((Mono<?>) result, publishResult, method, args, executionError);
        } else if (result instanceof Flux) {
            return handleFluxResult((Flux<?>) result, publishResult, method, args);
        } else {
            // Handle non-reactive result
            if (executionError != null) {
                publishEvent(executionError, publishResult, method, args);
                throw executionError;
            } else {
                publishEvent(result, publishResult, method, args);
                return result;
            }
        }
    }

    /**
     * Handles Mono return type by chaining event publishing.
     */
    private Mono<?> handleMonoResult(Mono<?> mono, PublishResult publishResult, 
                                     Method method, Object[] args, Throwable executionError) {
        if (executionError != null) {
            // Publish error event and return error Mono
            return Mono.fromRunnable(() -> publishEvent(executionError, publishResult, method, args))
                    .then(Mono.error(executionError));
        }
        
        return mono.flatMap(result -> {
            // Check condition before publishing
            if (!shouldPublish(result, publishResult, method, args)) {
                log.debug("Condition not met, skipping event publication");
                return Mono.just(result);
            }

            // Publish event
            Mono<Void> publishMono = publishEventReactive(result, publishResult, method, args);
            
            if (publishResult.async()) {
                // Async: don't wait for publishing to complete
                publishMono.subscribe(
                    v -> log.debug("Event published successfully (async)"),
                    error -> log.error("Failed to publish event (async)", error)
                );
                return Mono.just(result);
            } else {
                // Sync: wait for publishing to complete
                return publishMono.thenReturn(result);
            }
        }).onErrorResume(error -> {
            if (publishResult.publishOnError()) {
                return Mono.fromRunnable(() -> publishEvent(error, publishResult, method, args))
                        .then(Mono.error(error));
            }
            return Mono.error(error);
        });
    }

    /**
     * Handles Flux return type by publishing each element.
     */
    private Flux<?> handleFluxResult(Flux<?> flux, PublishResult publishResult, Method method, Object[] args) {
        return flux.flatMap(result -> {
            // Check condition before publishing
            if (!shouldPublish(result, publishResult, method, args)) {
                return Flux.just(result);
            }

            // Publish event
            Mono<Void> publishMono = publishEventReactive(result, publishResult, method, args);
            
            if (publishResult.async()) {
                // Async: don't wait for publishing
                publishMono.subscribe(
                    v -> log.debug("Event published successfully (async)"),
                    error -> log.error("Failed to publish event (async)", error)
                );
                return Flux.just(result);
            } else {
                // Sync: wait for publishing
                return publishMono.thenReturn(result);
            }
        }).onErrorResume(error -> {
            if (publishResult.publishOnError()) {
                return Mono.fromRunnable(() -> publishEvent(error, publishResult, method, args))
                        .thenMany(Flux.error(error));
            }
            return Flux.error(error);
        });
    }

    /**
     * Checks if the event should be published based on the condition.
     */
    private boolean shouldPublish(Object result, PublishResult publishResult, Method method, Object[] args) {
        String condition = publishResult.condition();
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        return spelEvaluator.evaluateCondition(condition, method, args, result);
    }

    /**
     * Publishes an event reactively.
     */
    private Mono<Void> publishEventReactive(Object result, PublishResult publishResult, 
                                            Method method, Object[] args) {
        try {
            // Get publisher
            EventPublisher publisher = getPublisher(publishResult);
            if (publisher == null || !publisher.isAvailable()) {
                log.warn("No available publisher for type: {}", publishResult.publisherType());
                return Mono.empty();
            }

            // Evaluate destination
            String destination = evaluateDestination(publishResult.destination(), method, args, result);
            if (destination == null || destination.isEmpty()) {
                log.warn("Destination is empty after evaluation, skipping publication");
                return Mono.empty();
            }

            // Build headers
            Map<String, Object> headers = buildHeaders(publishResult, method, args, result);

            // Publish event
            Mono<Void> publishMono = publisher.publish(result, destination, headers);
            
            // Apply timeout if configured
            if (publishResult.timeoutMs() > 0) {
                publishMono = publishMono.timeout(Duration.ofMillis(publishResult.timeoutMs()));
            }

            return publishMono
                    .doOnSuccess(v -> log.debug("Published event to destination: {}", destination))
                    .doOnError(error -> log.error("Failed to publish event to destination: {}", destination, error));
                    
        } catch (Exception e) {
            log.error("Error preparing event publication", e);
            return Mono.empty();
        }
    }

    /**
     * Publishes an event synchronously (for non-reactive methods).
     */
    private void publishEvent(Object result, PublishResult publishResult, Method method, Object[] args) {
        // Check condition
        if (!shouldPublish(result, publishResult, method, args)) {
            log.debug("Condition not met, skipping event publication");
            return;
        }

        publishEventReactive(result, publishResult, method, args)
                .doOnError(error -> log.error("Failed to publish event", error))
                .onErrorResume(error -> Mono.empty())
                .block(Duration.ofSeconds(30)); // Default timeout for sync publishing
    }

    /**
     * Gets the appropriate publisher based on annotation configuration.
     */
    private EventPublisher getPublisher(PublishResult publishResult) {
        String connectionId = publishResult.connectionId();
        String destination = publishResult.destination();
        
        if (connectionId != null && !connectionId.isEmpty()) {
            return publisherFactory.getPublisher(publishResult.publisherType(), connectionId);
        } else if (destination != null && !destination.isEmpty()) {
            return publisherFactory.getPublisher(publishResult.publisherType());
        } else {
            return publisherFactory.getDefaultPublisher();
        }
    }

    /**
     * Evaluates the destination using SpEL if needed.
     */
    private String evaluateDestination(String destination, Method method, Object[] args, Object result) {
        if (destination == null || destination.isEmpty()) {
            return null;
        }
        
        return spelEvaluator.evaluateAsString(destination, method, args, result);
    }

    /**
     * Builds headers map with SpEL evaluation.
     */
    private Map<String, Object> buildHeaders(PublishResult publishResult, Method method, 
                                            Object[] args, Object result) {
        Map<String, Object> headers = new HashMap<>();
        
        // Add event type
        if (publishResult.eventType() != null && !publishResult.eventType().isEmpty()) {
            headers.put("eventType", publishResult.eventType());
        }
        
        // Add message key if specified
        String keyExpression = publishResult.key();
        if (keyExpression != null && !keyExpression.isEmpty()) {
            String key = spelEvaluator.evaluateAsString(keyExpression, method, args, result);
            if (key != null) {
                headers.put("key", key);
            }
        }
        
        // Add custom headers
        Map<String, Object> customHeaders = spelEvaluator.evaluateHeaders(
                publishResult.headers(), method, args, result);
        headers.putAll(customHeaders);
        
        return headers;
    }
}

