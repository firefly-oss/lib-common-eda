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

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Interface for custom error handlers in event listeners.
 * <p>
 * Implementations of this interface can provide custom error handling logic
 * for event listeners that use the CUSTOM error handling strategy.
 * <p>
 * Custom error handlers can:
 * <ul>
 *   <li>Transform errors into different types</li>
 *   <li>Send notifications or alerts</li>
 *   <li>Implement custom retry logic</li>
 *   <li>Route errors to different destinations</li>
 *   <li>Perform cleanup operations</li>
 * </ul>
 */
public interface CustomErrorHandler {

    /**
     * Handles an error that occurred during event listener processing.
     *
     * @param event the original event that was being processed
     * @param headers the headers associated with the event
     * @param error the error that occurred
     * @param listenerMethod the listener method that failed
     * @return a Mono that completes when error handling is finished
     */
    Mono<Void> handleError(Object event, Map<String, Object> headers, 
                          Throwable error, String listenerMethod);

    /**
     * Returns the name of this error handler for identification purposes.
     *
     * @return the handler name
     */
    String getHandlerName();

    /**
     * Indicates whether this handler can handle the given error type.
     * <p>
     * This allows for selective error handling based on error types.
     *
     * @param errorType the type of error
     * @return true if this handler can handle the error type
     */
    default boolean canHandle(Class<? extends Throwable> errorType) {
        return true; // Handle all errors by default
    }

    /**
     * Returns the priority of this error handler.
     * <p>
     * When multiple custom error handlers are available, they are
     * executed in priority order (higher numbers = higher priority).
     *
     * @return the priority (default is 0)
     */
    default int getPriority() {
        return 0;
    }
}
