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

package com.firefly.common.eda.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for custom error handlers.
 * <p>
 * This component manages all available custom error handlers and provides
 * methods to execute them based on error types and priorities.
 */
@Component
@Slf4j
public class CustomErrorHandlerRegistry {

    private final List<CustomErrorHandler> errorHandlers = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private List<CustomErrorHandler> availableHandlers;

    @PostConstruct
    public void initializeHandlers() {
        if (availableHandlers != null && !availableHandlers.isEmpty()) {
            // Sort handlers by priority (highest first)
            availableHandlers.sort(Comparator.comparingInt(CustomErrorHandler::getPriority).reversed());
            errorHandlers.addAll(availableHandlers);
            
            log.info("Registered {} custom error handlers", errorHandlers.size());
            errorHandlers.forEach(handler -> 
                log.debug("Registered custom error handler: {} (priority: {})", 
                         handler.getHandlerName(), handler.getPriority()));
        } else {
            log.info("No custom error handlers found");
        }
    }

    /**
     * Handles an error using all applicable custom error handlers.
     *
     * @param event the original event
     * @param headers the event headers
     * @param error the error that occurred
     * @param listenerMethod the listener method that failed
     * @return a Mono that completes when all handlers have processed the error
     */
    public Mono<Void> handleError(Object event, Map<String, Object> headers,
                                 Throwable error, String listenerMethod) {
        if (error == null) {
            log.warn("Cannot handle null error");
            return Mono.empty();
        }

        if (errorHandlers.isEmpty()) {
            log.warn("No custom error handlers available for error: {}", error.getMessage());
            return Mono.empty();
        }

        // Find handlers that can handle this error type
        List<CustomErrorHandler> applicableHandlers = errorHandlers.stream()
                .filter(handler -> handler.canHandle(error.getClass()))
                .toList();

        if (applicableHandlers.isEmpty()) {
            log.warn("No custom error handlers can handle error type: {}", error.getClass().getSimpleName());
            return Mono.empty();
        }

        log.debug("Processing error with {} custom handlers", applicableHandlers.size());

        // Execute all applicable handlers
        return Flux.fromIterable(applicableHandlers)
                .flatMap(handler -> {
                    log.debug("Executing custom error handler: {}", handler.getHandlerName());
                    return handler.handleError(event, headers, error, listenerMethod)
                            .doOnSuccess(v -> log.debug("Custom error handler completed: {}", 
                                                      handler.getHandlerName()))
                            .doOnError(e -> log.error("Custom error handler failed: {}", 
                                                    handler.getHandlerName(), e))
                            .onErrorResume(e -> Mono.empty()); // Don't let handler errors propagate
                })
                .then()
                .doOnSuccess(v -> log.debug("All custom error handlers completed"))
                .doOnError(e -> log.error("Error in custom error handler execution", e));
    }

    /**
     * Registers a custom error handler programmatically.
     *
     * @param handler the handler to register
     */
    public void registerHandler(CustomErrorHandler handler) {
        if (handler != null) {
            errorHandlers.add(handler);
            // Re-sort by priority
            errorHandlers.sort(Comparator.comparingInt(CustomErrorHandler::getPriority).reversed());
            log.info("Registered custom error handler: {} (priority: {})", 
                    handler.getHandlerName(), handler.getPriority());
        }
    }

    /**
     * Unregisters a custom error handler.
     *
     * @param handlerName the name of the handler to unregister
     * @return true if the handler was found and removed
     */
    public boolean unregisterHandler(String handlerName) {
        boolean removed = errorHandlers.removeIf(handler -> 
            handler.getHandlerName().equals(handlerName));
        if (removed) {
            log.info("Unregistered custom error handler: {}", handlerName);
        }
        return removed;
    }

    /**
     * Gets all registered error handlers.
     *
     * @return a copy of the current handlers list
     */
    public List<CustomErrorHandler> getRegisteredHandlers() {
        return List.copyOf(errorHandlers);
    }

    /**
     * Checks if any custom error handlers are available.
     *
     * @return true if at least one handler is registered
     */
    public boolean hasHandlers() {
        return !errorHandlers.isEmpty();
    }
}
