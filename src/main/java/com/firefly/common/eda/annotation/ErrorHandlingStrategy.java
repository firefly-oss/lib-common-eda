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

/**
 * Error handling strategies for event processing failures.
 */
public enum ErrorHandlingStrategy {
    
    /**
     * Log the error and continue processing other events.
     * <p>
     * The failed event is acknowledged and will not be retried.
     */
    LOG_AND_CONTINUE,
    
    /**
     * Log the error and retry the event processing.
     * <p>
     * The event will be retried up to the configured maximum attempts.
     * If all retries fail, the event may be sent to a dead letter queue
     * or acknowledged based on the consumer configuration.
     */
    LOG_AND_RETRY,
    
    /**
     * Reject the event and stop processing.
     * <p>
     * The event will be negatively acknowledged and the consumer
     * may stop processing further events depending on the implementation.
     */
    REJECT_AND_STOP,
    
    /**
     * Send the failed event to a dead letter queue/topic.
     * <p>
     * The event will be acknowledged but forwarded to a configured
     * dead letter destination for manual inspection or processing.
     */
    DEAD_LETTER,
    
    /**
     * Ignore the error completely.
     * <p>
     * The event is acknowledged and processing continues.
     * No logging or retry attempts are made.
     */
    IGNORE,
    
    /**
     * Custom error handling.
     * <p>
     * The error handling is delegated to a custom error handler
     * defined in the application context.
     */
    CUSTOM;
    
    /**
     * Checks if this strategy includes retry logic.
     *
     * @return true if retries are performed
     */
    public boolean includesRetry() {
        return this == LOG_AND_RETRY;
    }
    
    /**
     * Checks if this strategy stops processing on error.
     *
     * @return true if processing should stop
     */
    public boolean stopsProcessing() {
        return this == REJECT_AND_STOP;
    }
    
    /**
     * Checks if this strategy acknowledges the message after error.
     *
     * @return true if the message should be acknowledged
     */
    public boolean acknowledgesMessage() {
        return switch (this) {
            case LOG_AND_CONTINUE, DEAD_LETTER, IGNORE -> true;
            case LOG_AND_RETRY, REJECT_AND_STOP, CUSTOM -> false;
        };
    }
}