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

import com.firefly.common.eda.annotation.ErrorHandlingStrategy;
import com.firefly.common.eda.annotation.EventListener;
import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processor for handling events consumed from messaging platforms.
 * <p>
 * This component discovers all methods annotated with @EventListener,
 * matches incoming events to appropriate listeners based on event type,
 * and invokes the listeners with proper error handling and metrics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Lazy
public class EventListenerProcessor {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<EventPublisherFactory> publisherFactoryProvider;

    // Cache of event type to list of listener methods
    private final Map<Class<?>, List<EventListenerMethod>> listenerCache = new ConcurrentHashMap<>();
    private final Map<String, List<EventListenerMethod>> topicListenerCache = new ConcurrentHashMap<>();

    // Cache for retry attempts per event
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();

    // Flag to track if listeners have been discovered
    private volatile boolean listenersDiscovered = false;

    /**
     * Lazy initialization of event listeners to avoid circular dependencies.
     * This method is called on first use rather than during bean construction.
     */
    private void ensureListenersDiscovered() {
        if (!listenersDiscovered) {
            synchronized (this) {
                if (!listenersDiscovered) {
                    log.info("Initializing EventListenerProcessor");
                    discoverEventListeners();
                    listenersDiscovered = true;
                    log.info("EventListenerProcessor initialized with {} event type mappings and {} topic mappings",
                            listenerCache.size(), topicListenerCache.size());
                }
            }
        }
    }

    /**
     * Processes an event by finding and invoking appropriate event listeners.
     *
     * @param event the event object to process
     * @param headers message headers from the messaging platform
     * @return Mono that completes when all listeners have been invoked
     */
    public Mono<Void> processEvent(Object event, Map<String, Object> headers) {
        if (event == null) {
            log.warn("Received null event, skipping processing");
            return Mono.empty();
        }

        // Ensure listeners are discovered before processing
        ensureListenersDiscovered();

        log.debug("Processing event: {} with headers: {}", event.getClass().getSimpleName(), headers);

        // Find listeners by event type
        List<EventListenerMethod> typeListeners = findListenersByType(event.getClass());
        
        // Find listeners by topic/destination (from headers)
        List<EventListenerMethod> topicListeners = findListenersByTopic(headers);
        
        // Combine and deduplicate listeners
        List<EventListenerMethod> combinedListeners = new ArrayList<>(typeListeners);
        topicListeners.stream()
                .filter(listener -> !combinedListeners.contains(listener))
                .forEach(combinedListeners::add);

        // Filter by consumer type if specified in headers
        List<EventListenerMethod> allListeners = filterByConsumerType(combinedListeners, headers);

        if (allListeners.isEmpty()) {
            log.debug("No event listeners found for event type: {}", event.getClass().getSimpleName());
            return Mono.empty();
        }

        log.debug("Found {} listeners for event: {}", allListeners.size(), event.getClass().getSimpleName());

        // Process all listeners in parallel
        return Flux.fromIterable(allListeners)
                .flatMap(listenerMethod -> invokeListener(listenerMethod, event, headers))
                .then()
                .timeout(Duration.ofSeconds(30)) // Global timeout for all listeners
                .onErrorResume(error -> {
                    log.error("Error processing event: {}", event.getClass().getSimpleName(), error);
                    return Mono.empty(); // Don't fail the entire processing chain
                });
    }

    /**
     * Discovers all methods annotated with @EventListener in the application context.
     */
    private void discoverEventListeners() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();
                
                // Check all methods for @EventListener annotation
                for (Method method : beanClass.getDeclaredMethods()) {
                    EventListener annotation = method.getAnnotation(EventListener.class);
                    if (annotation != null) {
                        registerEventListener(bean, method, annotation);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing bean '{}' for event listeners: {}", beanName, e.getMessage());
            }
        }
    }

    /**
     * Registers an event listener method.
     */
    private void registerEventListener(Object bean, Method method, EventListener annotation) {
        try {
            EventListenerMethod listenerMethod = new EventListenerMethod(bean, method, annotation);
            
            // Register by event types (if specified)
            String[] eventTypeNames = annotation.eventTypes();
            if (eventTypeNames.length > 0) {
                // Use specified event type names - store as strings for now
                // In a real implementation, we might want to resolve these to actual classes
                for (String eventTypeName : eventTypeNames) {
                    // For now, register under Object.class with the name as metadata
                    listenerCache.computeIfAbsent(Object.class, k -> new ArrayList<>()).add(listenerMethod);
                    log.debug("Registered event listener: {}.{} for event type: {}", 
                            bean.getClass().getSimpleName(), method.getName(), eventTypeName);
                }
            } else {
                // Infer event type from method parameter
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length > 0) {
                    Class<?> eventType = paramTypes[0];
                    listenerCache.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listenerMethod);
                    log.debug("Registered event listener: {}.{} for inferred event type: {}", 
                            bean.getClass().getSimpleName(), method.getName(), eventType.getSimpleName());
                }
            }
            
            // Register by destinations
            String[] topics = annotation.destinations();
            for (String topic : topics) {
                topicListenerCache.computeIfAbsent(topic, k -> new ArrayList<>()).add(listenerMethod);
                log.debug("Registered event listener: {}.{} for topic: {}", 
                        bean.getClass().getSimpleName(), method.getName(), topic);
            }
            
        } catch (Exception e) {
            log.error("Failed to register event listener: {}.{}", bean.getClass().getSimpleName(), method.getName(), e);
        }
    }

    /**
     * Finds event listeners by event type (including inheritance).
     */
    private List<EventListenerMethod> findListenersByType(Class<?> eventType) {
        List<EventListenerMethod> listeners = new ArrayList<>();
        
        // Direct type match
        listeners.addAll(listenerCache.getOrDefault(eventType, List.of()));
        
        // Check superclasses and interfaces
        Class<?> currentClass = eventType.getSuperclass();
        while (currentClass != null && currentClass != Object.class) {
            listeners.addAll(listenerCache.getOrDefault(currentClass, List.of()));
            currentClass = currentClass.getSuperclass();
        }
        
        // Check interfaces
        for (Class<?> interfaceClass : eventType.getInterfaces()) {
            listeners.addAll(listenerCache.getOrDefault(interfaceClass, List.of()));
        }
        
        return listeners;
    }

    /**
     * Finds event listeners by topic/destination from message headers.
     */
    private List<EventListenerMethod> findListenersByTopic(Map<String, Object> headers) {
        List<EventListenerMethod> listeners = new ArrayList<>();
        
        if (headers == null) {
            return listeners;
        }
        
        // Check various header keys that might contain topic/destination info
        String[] topicKeys = {"topic", "destination", "queue", "exchange", "subject"};
        
        for (String key : topicKeys) {
            Object value = headers.get(key);
            if (value != null) {
                String topic = value.toString();
                listeners.addAll(topicListenerCache.getOrDefault(topic, List.of()));
                
                // Also check for wildcard patterns
                for (Map.Entry<String, List<EventListenerMethod>> entry : topicListenerCache.entrySet()) {
                    if (matchesPattern(topic, entry.getKey())) {
                        listeners.addAll(entry.getValue());
                    }
                }
            }
        }
        
        return listeners;
    }

    /**
     * Filters listeners based on consumer type from headers.
     * Only listeners that match the consumer type or have no consumer type specified will be included.
     */
    private List<EventListenerMethod> filterByConsumerType(List<EventListenerMethod> listeners, Map<String, Object> headers) {
        if (headers == null || listeners.isEmpty()) {
            return listeners;
        }

        // Get the consumer type from headers
        Object consumerTypeHeader = headers.get("consumer_type");
        if (consumerTypeHeader == null) {
            // No consumer type in headers, return all listeners
            return listeners;
        }

        String headerConsumerType = consumerTypeHeader.toString();
        
        return listeners.stream()
                .filter(listener -> {
                    EventListener annotation = listener.getAnnotation();

                    // If no consumer type specified in annotation, include the listener
                    if (annotation.consumerType() == null || annotation.consumerType().name() == null) {
                        return true;
                    }

                    // Check if consumer type matches
                    String listenerConsumerType = annotation.consumerType().name();

                    // AUTO matches any consumer type
                    if ("AUTO".equals(listenerConsumerType)) {
                        log.debug("Consumer type filter: listener has AUTO, accepting event from '{}'", headerConsumerType);
                        return true;
                    }

                    boolean matches = listenerConsumerType.equals(headerConsumerType);

                    log.debug("Consumer type filter: listener requires '{}', event has '{}', matches: {}",
                            listenerConsumerType, headerConsumerType, matches);

                    return matches;
                })
                .toList();
    }

    /**
     * Simple pattern matching for topic wildcards (* and ?).
     */
    private boolean matchesPattern(String topic, String pattern) {
        if (pattern.equals("*") || pattern.equals(topic)) {
            return true;
        }
        
        // Convert glob pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        
        return topic.matches(regex);
    }

    /**
     * Invokes a single event listener method.
     */
    private Mono<Void> invokeListener(EventListenerMethod listenerMethod, Object event, Map<String, Object> headers) {
        EventListener annotation = listenerMethod.getAnnotation();

        Mono<Void> invocation = Mono.fromRunnable(() -> {
            try {
                log.debug("Invoking event listener: {}.{}",
                        listenerMethod.getBean().getClass().getSimpleName(),
                        listenerMethod.getMethod().getName());

                // Prepare method arguments
                Object[] args = prepareArguments(listenerMethod.getMethod(), event, headers);

                // Invoke the method
                listenerMethod.getMethod().invoke(listenerMethod.getBean(), args);

                log.debug("Successfully invoked event listener: {}.{}",
                        listenerMethod.getBean().getClass().getSimpleName(),
                        listenerMethod.getMethod().getName());

            } catch (Exception e) {
                throw new RuntimeException("Event listener invocation failed", e);
            }
        })
        .subscribeOn(annotation.async() ? Schedulers.parallel() : Schedulers.immediate())
        .timeout(Duration.ofMillis(annotation.timeoutMs() > 0 ? annotation.timeoutMs() : 30000))
        .then();

        // Apply retry logic if configured
        if (annotation.errorStrategy() == ErrorHandlingStrategy.LOG_AND_RETRY && annotation.maxRetries() > 0) {
            invocation = invocation.retryWhen(Retry.backoff(annotation.maxRetries(),
                    Duration.ofMillis(annotation.retryDelayMs()))
                    .doBeforeRetry(retrySignal -> {
                        log.warn("Retrying event listener {}.{} (attempt {}/{})",
                                listenerMethod.getBean().getClass().getSimpleName(),
                                listenerMethod.getMethod().getName(),
                                retrySignal.totalRetries() + 1,
                                annotation.maxRetries());
                    }));
        }

        // Handle errors based on strategy
        return invocation.onErrorResume(error -> handleListenerError(listenerMethod, event, headers, error));
    }

    /**
     * Prepares method arguments based on method signature.
     */
    private Object[] prepareArguments(Method method, Object event, Map<String, Object> headers) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            
            if (paramType.isAssignableFrom(event.getClass())) {
                args[i] = event;
            } else if (paramType == Map.class || paramType.isAssignableFrom(Map.class)) {
                args[i] = headers;
            } else {
                // Try to extract from headers by parameter name or type
                args[i] = null; // Default to null for unmatched parameters
            }
        }
        
        return args;
    }

    /**
     * Handles errors during listener invocation based on configured strategy.
     */
    private Mono<Void> handleListenerError(EventListenerMethod listenerMethod, Object event,
                                           Map<String, Object> headers, Throwable error) {
        ErrorHandlingStrategy strategy = listenerMethod.getAnnotation().errorStrategy();

        log.error("Error in event listener: {}.{}, strategy: {}",
                listenerMethod.getBean().getClass().getSimpleName(),
                listenerMethod.getMethod().getName(),
                strategy, error);

        return switch (strategy) {
            case IGNORE -> {
                log.debug("Ignoring error in event listener as per configured strategy");
                yield Mono.empty();
            }
            case LOG_AND_CONTINUE -> {
                // Error already logged above
                yield Mono.empty();
            }
            case LOG_AND_RETRY -> {
                // Retry logic is handled in invokeListener via retryWhen
                // If we reach here, all retries have been exhausted
                log.error("All retry attempts exhausted for event listener: {}.{}",
                        listenerMethod.getBean().getClass().getSimpleName(),
                        listenerMethod.getMethod().getName());
                yield Mono.empty();
            }
            case DEAD_LETTER -> sendToDeadLetterQueue(event, headers, error);
            case REJECT_AND_STOP -> Mono.error(new RuntimeException(
                    "Event listener failed with REJECT_AND_STOP strategy", error));
            case CUSTOM -> {
                log.warn("Custom strategy not yet implemented, treating as LOG_AND_CONTINUE");
                yield Mono.empty();
            }
        };
    }

    /**
     * Sends a failed event to the dead letter queue.
     */
    private Mono<Void> sendToDeadLetterQueue(Object event, Map<String, Object> headers, Throwable error) {
        EventPublisherFactory publisherFactory = publisherFactoryProvider.getIfAvailable();

        if (publisherFactory == null) {
            log.warn("Cannot send to dead letter queue - EventPublisherFactory not available");
            return Mono.empty();
        }

        try {
            // Get the default publisher for dead letter queue
            EventPublisher publisher = publisherFactory.getDefaultPublisher();

            if (publisher == null || !publisher.isAvailable()) {
                log.warn("Cannot send to dead letter queue - no available publisher");
                return Mono.empty();
            }

            // Determine dead letter destination
            String deadLetterDestination = determineDeadLetterDestination(headers);

            // Add error information to headers
            Map<String, Object> dlqHeaders = new HashMap<>(headers);
            dlqHeaders.put("dlq_reason", "listener_error");
            dlqHeaders.put("dlq_error_message", error.getMessage());
            dlqHeaders.put("dlq_error_class", error.getClass().getName());
            dlqHeaders.put("dlq_timestamp", System.currentTimeMillis());
            dlqHeaders.put("dlq_original_destination", headers.get("destination"));

            log.info("Sending failed event to dead letter queue: {}", deadLetterDestination);

            return publisher.publish(event, deadLetterDestination, dlqHeaders)
                    .doOnSuccess(v -> log.info("Successfully sent event to dead letter queue"))
                    .doOnError(e -> log.error("Failed to send event to dead letter queue", e))
                    .onErrorResume(e -> Mono.empty()); // Don't fail if DLQ send fails

        } catch (Exception e) {
            log.error("Error sending event to dead letter queue", e);
            return Mono.empty();
        }
    }

    /**
     * Determines the dead letter destination based on the original destination.
     */
    private String determineDeadLetterDestination(Map<String, Object> headers) {
        Object originalDestination = headers.get("destination");

        if (originalDestination != null) {
            return originalDestination + ".dlq";
        }

        // Fallback to a default dead letter queue
        return "dead-letter-queue";
    }

    /**
     * Inner class to hold event listener method metadata.
     */
    private static class EventListenerMethod {
        private final Object bean;
        private final Method method;
        private final EventListener annotation;

        public EventListenerMethod(Object bean, Method method, EventListener annotation) {
            this.bean = bean;
            this.method = method;
            this.annotation = annotation;
            
            // Make method accessible if needed
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
        }

        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
        public EventListener getAnnotation() { return annotation; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EventListenerMethod other)) return false;
            return bean.equals(other.bean) && method.equals(other.method);
        }

        @Override
        public int hashCode() {
            return bean.hashCode() * 31 + method.hashCode();
        }
    }
}