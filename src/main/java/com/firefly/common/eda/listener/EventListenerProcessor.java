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
import com.firefly.common.eda.error.CustomErrorHandlerRegistry;
import com.firefly.common.eda.event.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processor for handling events consumed from messaging platforms.
 * <p>
 * This component discovers all methods annotated with @EventListener,
 * matches incoming events to appropriate listeners based on event type,
 * and invokes the listeners with proper error handling and metrics.
 * <p>
 * This processor is designed to avoid circular dependencies by using
 * Spring's ApplicationEventPublisher for dead letter queue functionality
 * instead of directly depending on EventPublisherFactory.
 * <p>
 * Uses {@code ApplicationListener<ContextRefreshedEvent>} to initialize event listeners
 * after all beans are fully initialized, avoiding circular reference issues.
 */
@Component
@Slf4j
public class EventListenerProcessor implements ApplicationListener<ContextRefreshedEvent>, DynamicEventListenerRegistry {

    private final ApplicationContext applicationContext;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CustomErrorHandlerRegistry customErrorHandlerRegistry;
    private final Environment environment;

    // Optional deduplication service
    @Autowired(required = false)
    private com.firefly.common.eda.dedup.DeduplicationService deduplicationService;

    // Cache of event type to list of listener methods
    private final Map<Class<?>, List<EventListenerMethod>> listenerCache = new ConcurrentHashMap<>();
    private final Map<String, List<EventListenerMethod>> topicListenerCache = new ConcurrentHashMap<>();

    // Cache for retry attempts per event
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();

    // Dynamic listeners registered programmatically
    private final Map<String, DynamicListenerHolder> dynamicListeners = new ConcurrentHashMap<>();

    // Callbacks to notify when listeners are registered/unregistered
    private final List<Runnable> listenerChangeCallbacks = new ArrayList<>();

    // Flag to ensure initialization happens only once
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public EventListenerProcessor(ApplicationContext applicationContext,
                                ApplicationEventPublisher applicationEventPublisher,
                                CustomErrorHandlerRegistry customErrorHandlerRegistry,
                                Environment environment) {
        this.applicationContext = applicationContext;
        this.applicationEventPublisher = applicationEventPublisher;
        this.customErrorHandlerRegistry = customErrorHandlerRegistry;
        this.environment = environment;
    }

    /**
     * Initialize event listeners after the application context has been refreshed.
     * This ensures all beans are fully initialized before we try to discover listeners,
     * avoiding circular reference issues.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Only initialize once, even if multiple ContextRefreshedEvents are fired
        if (initialized.compareAndSet(false, true)) {
            initializeEventListeners();
        }
    }

    /**
     * Public method to initialize event listeners.
     * Used both by the InitializingBean callback and for testing.
     */
    public void initializeEventListeners() {
        log.info("Initializing EventListenerProcessor");
        discoverEventListeners();
        log.info("EventListenerProcessor initialized with {} event type mappings and {} topic mappings",
                listenerCache.size(), topicListenerCache.size());
    }
    /**
     * Gets all topics/destinations registered from @EventListener annotations.
     * This is used by Kafka consumer to dynamically subscribe to topics.
     *
     * @return Set of all registered topic names
     */
    public java.util.Set<String> getAllRegisteredTopics() {
        return new java.util.HashSet<>(topicListenerCache.keySet());
    }

    /**
     * Gets all topics/destinations registered from @EventListener annotations for a specific consumer type.
     *
     * @param consumerType the consumer type (e.g., "KAFKA", "RABBITMQ")
     * @return Set of topic names for the specified consumer type
     */
    public java.util.Set<String> getTopicsForConsumerType(String consumerType) {
        log.debug("🔍 getTopicsForConsumerType({}) called - topicListenerCache has {} entries",
                 consumerType, topicListenerCache.size());

        java.util.Set<String> topics = new java.util.HashSet<>();

        topicListenerCache.forEach((topic, listeners) -> {
            log.debug("  Checking topic '{}' with {} listeners", topic, listeners.size());
            for (EventListenerMethod listener : listeners) {
                com.firefly.common.eda.annotation.PublisherType type = listener.getAnnotation().consumerType();
                log.debug("    Listener consumerType: {}", type != null ? type.name() : "null");
                if (type != null && (type.name().equals(consumerType) || type.name().equals("AUTO"))) {
                    topics.add(topic);
                    log.debug("    ✅ Added topic '{}' for consumerType {}", topic, consumerType);
                    break; // Found at least one listener for this topic with matching consumer type
                }
            }
        });

        log.debug("📋 Returning {} topics for consumerType {}: {}", topics.size(), consumerType, topics);
        return topics;
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

        // If deduplication is available, guard processing with it
        if (deduplicationService != null) {
            return deduplicationService.tryAcquire(event, headers)
                    .flatMap(allowed -> allowed ? doProcessEvent(event, headers) : Mono.empty());
        }

        return doProcessEvent(event, headers);
    }

    // Visible for tests
    void setDeduplicationService(com.firefly.common.eda.dedup.DeduplicationService service) {
        this.deduplicationService = service;
    }

    private Mono<Void> doProcessEvent(Object event, Map<String, Object> headers) {
        // Listeners are already discovered during @PostConstruct

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
     * This method is called after the context is fully refreshed, so all beans should be available.
     */
    private void discoverEventListeners() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        int discoveredCount = 0;

        for (String beanName : beanNames) {
            try {
                // Skip internal Spring beans and infrastructure beans
                if (beanName.startsWith("org.springframework") ||
                    beanName.startsWith("spring.") ||
                    beanName.contains("InternalConfigurationAnnotationProcessor")) {
                    continue;
                }

                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();

                // Check all methods for @EventListener annotation
                for (Method method : beanClass.getDeclaredMethods()) {
                    EventListener annotation = method.getAnnotation(EventListener.class);
                    if (annotation != null) {
                        registerEventListener(bean, method, annotation);
                        discoveredCount++;
                    }
                }
            } catch (org.springframework.beans.factory.BeanCreationException e) {
                // This should not happen after ContextRefreshedEvent, but log it just in case
                log.debug("Skipping bean '{}' - still in creation: {}", beanName, e.getMessage());
            } catch (Exception e) {
                log.debug("Skipping bean '{}' for event listener discovery: {}", beanName, e.getMessage());
            }
        }

        log.debug("Discovered {} event listener methods across {} beans", discoveredCount, beanNames.length);
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
                // Resolve event type names to actual classes
                for (String eventTypeName : eventTypeNames) {
                    Class<?> eventTypeClass = resolveEventTypeClass(eventTypeName, method);
                    if (eventTypeClass != null) {
                        listenerCache.computeIfAbsent(eventTypeClass, k -> new ArrayList<>()).add(listenerMethod);
                        log.debug("Registered event listener: {}.{} for event type: {}",
                                bean.getClass().getSimpleName(), method.getName(), eventTypeName);
                    } else {
                        log.warn("Could not resolve event type '{}' for listener {}.{}",
                                eventTypeName, bean.getClass().getSimpleName(), method.getName());
                    }
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
                // Resolve Spring property placeholders
                String resolvedTopic = environment.resolvePlaceholders(topic);
                
                // Skip empty or unresolved placeholders
                if (resolvedTopic.isEmpty() || resolvedTopic.startsWith("${")) {
                    log.debug("Skipping empty or unresolved topic placeholder: {}", topic);
                    continue;
                }
                
                topicListenerCache.computeIfAbsent(resolvedTopic, k -> new ArrayList<>()).add(listenerMethod);
                log.debug("Registered event listener: {}.{} for topic: {} (resolved from: {})", 
                        bean.getClass().getSimpleName(), method.getName(), resolvedTopic, topic);
            }
            
        } catch (Exception e) {
            log.error("Failed to register event listener: {}.{}", bean.getClass().getSimpleName(), method.getName(), e);
        }
    }

    /**
     * Resolves an event type name to its corresponding Class object.
     * Tries multiple strategies to find the class.
     */
    private Class<?> resolveEventTypeClass(String eventTypeName, Method method) {
        try {
            // Strategy 1: Try to find the class by simple name in the same package as the listener method
            String packageName = method.getDeclaringClass().getPackage().getName();
            try {
                return Class.forName(packageName + "." + eventTypeName);
            } catch (ClassNotFoundException e) {
                // Continue to next strategy
            }

            // Strategy 2: Try common test packages
            String[] commonPackages = {
                "com.firefly.common.eda.testconfig",
                packageName.replace(".listener", ".testconfig"),
                packageName.replace(".test", ".testconfig")
            };

            for (String pkg : commonPackages) {
                try {
                    return Class.forName(pkg + "." + eventTypeName);
                } catch (ClassNotFoundException e) {
                    // Continue to next package
                }
            }

            // Strategy 3: Try as nested class in TestEventModels
            String[] nestedClassPatterns = {
                "com.firefly.common.eda.testconfig.TestEventModels$" + eventTypeName,
                packageName.replace(".listener", ".testconfig") + ".TestEventModels$" + eventTypeName,
                packageName + ".TestEventModels$" + eventTypeName
            };

            for (String nestedClassName : nestedClassPatterns) {
                try {
                    return Class.forName(nestedClassName);
                } catch (ClassNotFoundException e) {
                    // Continue to next pattern
                }
            }

            // Strategy 4: Try as fully qualified name
            return Class.forName(eventTypeName);

        } catch (ClassNotFoundException e) {
            log.debug("Could not resolve event type class for name: {}", eventTypeName);
            return null;
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

        Mono<Void> invocation = Mono.defer(() -> {
            try {
                log.debug("Invoking event listener: {}.{}",
                        listenerMethod.getBean().getClass().getSimpleName(),
                        listenerMethod.getMethod().getName());

                // Prepare method arguments
                Object[] args = prepareArguments(listenerMethod.getMethod(), event, headers);

                // Invoke the method
                Object result = listenerMethod.getMethod().invoke(listenerMethod.getBean(), args);

                log.debug("Successfully invoked event listener: {}.{}",
                        listenerMethod.getBean().getClass().getSimpleName(),
                        listenerMethod.getMethod().getName());

                // If the method returns a Mono, subscribe to it
                if (result instanceof Mono) {
                    return ((Mono<?>) result).then();
                } else {
                    return Mono.empty();
                }

            } catch (Exception e) {
                return Mono.error(new RuntimeException("Event listener invocation failed", e));
            }
        })
        .subscribeOn(annotation.async() ? Schedulers.parallel() : Schedulers.immediate())
        .timeout(Duration.ofMillis(annotation.timeoutMs() > 0 ? annotation.timeoutMs() : 30000));

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

            // Check if parameter expects EventEnvelope
            if (paramType == EventEnvelope.class || paramType.isAssignableFrom(EventEnvelope.class)) {
                // Create EventEnvelope wrapping the event with headers
                String destination = (String) headers.getOrDefault("destination", "unknown");
                String eventType = event.getClass().getSimpleName();
                String consumerType = (String) headers.getOrDefault("consumer_type", "KAFKA");

                args[i] = new EventEnvelope(
                        destination,
                        eventType,
                        event,
                        (String) headers.get("transactionId"),
                        headers,
                        EventEnvelope.EventMetadata.empty(),
                        java.time.Instant.now(),
                        null, // publisherType
                        consumerType,
                        (String) headers.getOrDefault("connection_id", "default"),
                        null  // ackCallback
                );
            } else if (paramType.isAssignableFrom(event.getClass())) {
                // Direct event object
                args[i] = event;
            } else if (paramType == Map.class || paramType.isAssignableFrom(Map.class)) {
                // Headers map
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
            case CUSTOM -> handleCustomErrorStrategy(listenerMethod, event, headers, error);
        };
    }

    /**
     * Handles errors using the CUSTOM error handling strategy.
     */
    private Mono<Void> handleCustomErrorStrategy(EventListenerMethod listenerMethod, Object event,
                                               Map<String, Object> headers, Throwable error) {
        if (!customErrorHandlerRegistry.hasHandlers()) {
            log.warn("CUSTOM error strategy specified but no custom error handlers available, " +
                    "falling back to LOG_AND_CONTINUE");
            return Mono.empty();
        }

        String listenerMethodName = listenerMethod.getBean().getClass().getSimpleName() +
                                   "." + listenerMethod.getMethod().getName();

        return customErrorHandlerRegistry.handleError(event, headers, error, listenerMethodName)
                .doOnSuccess(v -> log.debug("Custom error handling completed for: {}", listenerMethodName))
                .doOnError(e -> log.error("Custom error handling failed for: {}", listenerMethodName, e))
                .onErrorResume(e -> Mono.empty()); // Don't propagate custom handler errors
    }

    /**
     * Sends a failed event to the dead letter queue by publishing a DeadLetterQueueEvent.
     * <p>
     * This approach avoids circular dependencies by using Spring's event publishing
     * mechanism instead of directly depending on EventPublisherFactory.
     */
    private Mono<Void> sendToDeadLetterQueue(Object event, Map<String, Object> headers, Throwable error) {
        try {
            // Determine dead letter destination
            String deadLetterDestination = determineDeadLetterDestination(headers);

            // Create and publish dead letter queue event
            DeadLetterQueueEvent dlqEvent = new DeadLetterQueueEvent(
                this, event, headers, error, deadLetterDestination
            );

            log.info("Publishing dead letter queue event for failed event: {}", deadLetterDestination);
            applicationEventPublisher.publishEvent(dlqEvent);

            return Mono.empty();

        } catch (Exception e) {
            log.error("Error publishing dead letter queue event", e);
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

    // ==================== DynamicEventListenerRegistry Implementation ====================
    
    @Override
    public void registerListener(
            String listenerId,
            String destination,
            PublisherType consumerType,
            java.util.function.BiFunction<Object, Map<String, Object>, Mono<Void>> handler) {
        registerListener(listenerId, destination, new String[0], consumerType, handler);
    }

    @Override
    public void registerListener(
            String listenerId,
            String destination,
            String[] eventTypes,
            PublisherType consumerType,
            java.util.function.BiFunction<Object, Map<String, Object>, Mono<Void>> handler) {
        
        log.info("Registering dynamic listener: id={}, destination={}, consumerType={}, eventTypes={}",
                listenerId, destination, consumerType, java.util.Arrays.toString(eventTypes));
        
        // Create holder for this dynamic listener
        DynamicListenerHolder holder = new DynamicListenerHolder(
                listenerId, destination, eventTypes, consumerType, handler);
        
        dynamicListeners.put(listenerId, holder);
        
        // Register in topic cache so consumers can discover it
        topicListenerCache.computeIfAbsent(destination, k -> new ArrayList<>())
                .add(holder.toEventListenerMethod());
        
        log.info("Dynamic listener registered successfully: {}", listenerId);
        
        // Notify consumers that a new listener has been registered
        notifyListenerChange();
    }

    @Override
    public void unregisterListener(String listenerId) {
        log.info("Unregistering dynamic listener: {}", listenerId);
        
        DynamicListenerHolder holder = dynamicListeners.remove(listenerId);
        if (holder != null) {
            // Remove from topic cache
            List<EventListenerMethod> listeners = topicListenerCache.get(holder.destination);
            if (listeners != null) {
                listeners.removeIf(l -> l.getBean() == holder);
            }
            log.info("Dynamic listener unregistered: {}", listenerId);
            // Notify consumers that a listener has been removed
            notifyListenerChange();
        } else {
            log.warn("Attempted to unregister unknown listener: {}", listenerId);
        }
    }

    @Override
    public boolean isListenerRegistered(String listenerId) {
        return dynamicListeners.containsKey(listenerId);
    }

    @Override
    public String[] getRegisteredListenerIds() {
        return dynamicListeners.keySet().toArray(new String[0]);
    }
    
    /**
     * Registers a callback to be notified when listeners are added or removed.
     * This allows consumers to refresh their topic subscriptions dynamically.
     *
     * @param callback the callback to invoke on listener changes
     */
    public void registerListenerChangeCallback(Runnable callback) {
        listenerChangeCallbacks.add(callback);
        log.debug("Registered listener change callback");
    }
    
    /**
     * Notifies all registered callbacks that listeners have changed.
     */
    private void notifyListenerChange() {
        log.debug("Notifying {} callbacks of listener change", listenerChangeCallbacks.size());
        listenerChangeCallbacks.forEach(callback -> {
            try {
                callback.run();
            } catch (Exception e) {
                log.error("Error in listener change callback", e);
            }
        });
    }
    
    /**
     * Holder for dynamically registered listeners.
     */
    private static class DynamicListenerHolder {
        private final String listenerId;
        private final String destination;
        private final String[] eventTypes;
        private final PublisherType consumerType;
        private final java.util.function.BiFunction<Object, Map<String, Object>, Mono<Void>> handler;
        
        public DynamicListenerHolder(
                String listenerId,
                String destination,
                String[] eventTypes,
                PublisherType consumerType,
                java.util.function.BiFunction<Object, Map<String, Object>, Mono<Void>> handler) {
            this.listenerId = listenerId;
            this.destination = destination;
            this.eventTypes = eventTypes;
            this.consumerType = consumerType;
            this.handler = handler;
        }
        
        /**
         * Converts this dynamic listener to an EventListenerMethod for compatibility.
         */
        public EventListenerMethod toEventListenerMethod() {
            // Create a synthetic EventListener annotation
            EventListener annotation = new EventListener() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return EventListener.class;
                }
                
                @Override
                public String[] destinations() { return new String[]{destination}; }
                
                @Override
                public String[] eventTypes() { return eventTypes; }
                
                @Override
                public PublisherType consumerType() { return consumerType; }
                
                @Override
                public String connectionId() { return "default"; }
                
                @Override
                public com.firefly.common.eda.annotation.ErrorHandlingStrategy errorStrategy() {
                    return com.firefly.common.eda.annotation.ErrorHandlingStrategy.LOG_AND_CONTINUE;
                }
                
                @Override
                public int maxRetries() { return 3; }
                
                @Override
                public long retryDelayMs() { return 1000; }
                
                @Override
                public boolean autoAck() { return true; }
                
                @Override
                public String groupId() { return ""; }
                
                @Override
                public String condition() { return ""; }
                
                @Override
                public int priority() { return 0; }
                
                @Override
                public boolean async() { return true; }
                
                @Override
                public long timeoutMs() { return 0; }
            };
            
            // Create a synthetic method
            try {
                Method method = DynamicListenerHolder.class.getDeclaredMethod("handleEvent", Object.class, Map.class);
                return new EventListenerMethod(this, method, annotation);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Failed to create synthetic method", e);
            }
        }
        
        /**
         * Handler method that will be invoked by reflection.
         */
        @SuppressWarnings("unused")
        public Mono<Void> handleEvent(Object event, Map<String, Object> headers) {
            return handler.apply(event, headers);
        }
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