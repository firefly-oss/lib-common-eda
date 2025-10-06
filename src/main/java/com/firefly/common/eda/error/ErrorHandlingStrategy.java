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

/**
 * Enumeration of error handling strategies for event listeners.
 * <p>
 * These strategies define how the EDA library should handle errors
 * that occur during event processing in event listeners.
 */
public enum ErrorHandlingStrategy {
    
    /**
     * Ignore errors and continue processing other events.
     * <p>
     * Errors are silently ignored and do not affect other event processing.
     * This strategy provides maximum resilience but may hide important issues.
     */
    IGNORE,
    
    /**
     * Log errors and continue processing other events.
     * <p>
     * Errors are logged at ERROR level and processing continues.
     * This is the default strategy and provides a good balance between
     * resilience and observability.
     */
    LOG_AND_CONTINUE,
    
    /**
     * Retry failed event processing.
     * <p>
     * Failed events will be retried according to configured retry policies.
     * After maximum retries are exhausted, falls back to LOG_AND_CONTINUE.
     */
    RETRY,
    
    /**
     * Send failed events to a dead letter queue/topic.
     * <p>
     * Failed events are sent to a configured dead letter destination
     * for later analysis and potential reprocessing.
     */
    DEAD_LETTER,
    
    /**
     * Fail fast by propagating the error up the call stack.
     * <p>
     * This strategy will cause the consumer to stop processing and
     * may affect the entire application. Use with caution.
     */
    FAIL_FAST;
    
    /**
     * Gets a human-readable description of the error handling strategy.
     *
     * @return the description
     */
    public String getDescription() {
        return switch (this) {
            case IGNORE -> "Ignore errors silently";
            case LOG_AND_CONTINUE -> "Log errors and continue processing";
            case RETRY -> "Retry failed events with backoff";
            case DEAD_LETTER -> "Send failed events to dead letter queue";
            case FAIL_FAST -> "Fail fast and stop processing";
        };
    }
    
    /**
     * Checks if this strategy allows processing to continue after an error.
     *
     * @return true if processing can continue
     */
    public boolean allowsContinuation() {
        return this != FAIL_FAST;
    }
    
    /**
     * Checks if this strategy requires retry configuration.
     *
     * @return true if retry configuration is needed
     */
    public boolean requiresRetryConfig() {
        return this == RETRY;
    }
    
    /**
     * Checks if this strategy requires dead letter configuration.
     *
     * @return true if dead letter configuration is needed
     */
    public boolean requiresDeadLetterConfig() {
        return this == DEAD_LETTER;
    }
}