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

import com.firefly.common.eda.annotation.EventPublisher;
import com.firefly.common.eda.expression.SpelExpressionEvaluator;
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
 * Aspect that intercepts methods annotated with @EventPublisher and automatically
 * publishes method parameters as events.
 * <p>
 * This aspect supports:
 * <ul>
 *   <li>Publishing before method execution (BEFORE)</li>
 *   <li>Publishing after method execution (AFTER)</li>
 *   <li>Publishing both before and after (BOTH)</li>
 *   <li>Publishing specific parameter by index</li>
 *   <li>Publishing all parameters as a map (parameterIndex = -1)</li>
 *   <li>SpEL expressions in destination, condition, key, and headers</li>
 *   <li>Conditional publishing based on parameters</li>
 *   <li>Async and sync publishing</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @EventPublisher(
 *     destination = "#{#param0.tenantId}-commands",
 *     eventType = "user.create.command",
 *     parameterIndex = 0,
 *     timing = PublishTiming.BEFORE,
 *     condition = "#param0 != null"
 * )
 * public Mono<User> createUser(CreateUserCommand command) {
 *     return userRepository.save(command.toUser());
 * }
 * }
 * </pre>
 */
@Aspect
@Component
@Order(99) // Execute before PublishResultAspect
@RequiredArgsConstructor
@Slf4j
public class EventPublisherAspect {

    private final EventPublisherFactory publisherFactory;
    private final SpelExpressionEvaluator spelEvaluator;

    /**
     * Intercepts methods annotated with @EventPublisher.
     */
    @Around("@annotation(eventPublisher)")
    public Object publishEvent(ProceedingJoinPoint joinPoint, EventPublisher eventPublisher) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        log.debug("Intercepting method {} with @EventPublisher", method.getName());

        EventPublisher.PublishTiming timing = eventPublisher.timing();

        // Publish BEFORE if needed
        if (timing == EventPublisher.PublishTiming.BEFORE || timing == EventPublisher.PublishTiming.BOTH) {
            publishParameters(eventPublisher, method, args, null);
        }

        // Proceed with method execution
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            // If AFTER timing, still try to publish on error
            if (timing == EventPublisher.PublishTiming.AFTER || timing == EventPublisher.PublishTiming.BOTH) {
                publishParameters(eventPublisher, method, args, null);
            }
            throw throwable;
        }

        // Publish AFTER if needed
        if (timing == EventPublisher.PublishTiming.AFTER || timing == EventPublisher.PublishTiming.BOTH) {
            // Handle reactive return types
            if (result instanceof Mono) {
                return handleMonoResult((Mono<?>) result, eventPublisher, method, args);
            } else if (result instanceof Flux) {
                return handleFluxResult((Flux<?>) result, eventPublisher, method, args);
            } else {
                // Non-reactive: publish immediately
                publishParameters(eventPublisher, method, args, result);
                return result;
            }
        }

        return result;
    }

    /**
     * Handles Mono return type for AFTER timing.
     */
    private Mono<?> handleMonoResult(Mono<?> mono, EventPublisher eventPublisher, 
                                     Method method, Object[] args) {
        return mono.doOnSuccess(result -> {
            publishParameters(eventPublisher, method, args, result);
        }).doOnError(error -> {
            // Publish even on error for AFTER timing
            publishParameters(eventPublisher, method, args, null);
        });
    }

    /**
     * Handles Flux return type for AFTER timing.
     */
    private Flux<?> handleFluxResult(Flux<?> flux, EventPublisher eventPublisher, 
                                     Method method, Object[] args) {
        return flux.doOnComplete(() -> {
            publishParameters(eventPublisher, method, args, null);
        }).doOnError(error -> {
            // Publish even on error for AFTER timing
            publishParameters(eventPublisher, method, args, null);
        });
    }

    /**
     * Publishes method parameters as an event.
     */
    private void publishParameters(EventPublisher eventPublisher, Method method, 
                                   Object[] args, Object result) {
        try {
            // Check condition
            if (!shouldPublish(eventPublisher, method, args, result)) {
                log.debug("Condition not met, skipping event publication");
                return;
            }

            // Get the event payload
            Object eventPayload = getEventPayload(eventPublisher, args);
            if (eventPayload == null) {
                log.debug("Event payload is null, skipping publication");
                return;
            }

            // Get publisher
            com.firefly.common.eda.publisher.EventPublisher publisher = getPublisher(eventPublisher);
            if (publisher == null || !publisher.isAvailable()) {
                log.warn("No available publisher for type: {}", eventPublisher.publisherType());
                return;
            }

            // Evaluate destination
            String destination = evaluateDestination(eventPublisher.destination(), method, args, result);
            if (destination == null || destination.isEmpty()) {
                log.warn("Destination is empty after evaluation, skipping publication");
                return;
            }

            // Build headers
            Map<String, Object> headers = buildHeaders(eventPublisher, method, args, result);

            // Publish event
            Mono<Void> publishMono = publisher.publish(eventPayload, destination, headers);
            
            // Apply timeout if configured
            if (eventPublisher.timeoutMs() > 0) {
                publishMono = publishMono.timeout(Duration.ofMillis(eventPublisher.timeoutMs()));
            }

            if (eventPublisher.async()) {
                // Async: don't wait for publishing
                publishMono.subscribe(
                    v -> log.debug("Event published successfully (async) to: {}", destination),
                    error -> log.error("Failed to publish event (async) to: {}", destination, error)
                );
            } else {
                // Sync: wait for publishing
                publishMono
                    .doOnSuccess(v -> log.debug("Event published successfully (sync) to: {}", destination))
                    .doOnError(error -> log.error("Failed to publish event (sync) to: {}", destination, error))
                    .onErrorResume(error -> Mono.empty())
                    .block(Duration.ofSeconds(30));
            }

        } catch (Exception e) {
            log.error("Error publishing event from method parameters", e);
        }
    }

    /**
     * Gets the event payload based on parameterIndex configuration.
     */
    private Object getEventPayload(EventPublisher eventPublisher, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        int parameterIndex = eventPublisher.parameterIndex();

        if (parameterIndex == -1) {
            // Publish all parameters as a map
            Map<String, Object> parametersMap = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                parametersMap.put("param" + i, args[i]);
            }
            return parametersMap;
        } else if (parameterIndex >= 0 && parameterIndex < args.length) {
            // Publish specific parameter
            return args[parameterIndex];
        } else {
            log.warn("Invalid parameterIndex: {}. Args length: {}", parameterIndex, args.length);
            return null;
        }
    }

    /**
     * Checks if the event should be published based on the condition.
     */
    private boolean shouldPublish(EventPublisher eventPublisher, Method method, 
                                  Object[] args, Object result) {
        String condition = eventPublisher.condition();
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        return spelEvaluator.evaluateCondition(condition, method, args, result);
    }

    /**
     * Gets the appropriate publisher based on annotation configuration.
     */
    private com.firefly.common.eda.publisher.EventPublisher getPublisher(EventPublisher eventPublisher) {
        String connectionId = eventPublisher.connectionId();
        String destination = eventPublisher.destination();
        
        if (connectionId != null && !connectionId.isEmpty()) {
            return publisherFactory.getPublisher(eventPublisher.publisherType(), connectionId);
        } else if (destination != null && !destination.isEmpty()) {
            return publisherFactory.getPublisher(eventPublisher.publisherType());
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
    private Map<String, Object> buildHeaders(EventPublisher eventPublisher, Method method, 
                                            Object[] args, Object result) {
        Map<String, Object> headers = new HashMap<>();
        
        // Add event type
        if (eventPublisher.eventType() != null && !eventPublisher.eventType().isEmpty()) {
            headers.put("eventType", eventPublisher.eventType());
        }
        
        // Add message key if specified
        String keyExpression = eventPublisher.key();
        if (keyExpression != null && !keyExpression.isEmpty()) {
            String key = spelEvaluator.evaluateAsString(keyExpression, method, args, result);
            if (key != null) {
                headers.put("key", key);
            }
        }
        
        // Add custom headers
        Map<String, Object> customHeaders = spelEvaluator.evaluateHeaders(
                eventPublisher.headers(), method, args, result);
        headers.putAll(customHeaders);
        
        return headers;
    }
}

