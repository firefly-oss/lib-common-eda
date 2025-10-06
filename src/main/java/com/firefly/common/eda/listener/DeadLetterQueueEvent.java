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

import lombok.Builder;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Event published when a message needs to be sent to the dead letter queue.
 * <p>
 * This event is used to break the circular dependency between EventListenerProcessor
 * and EventPublisherFactory. Instead of directly using the publisher factory,
 * the processor publishes this event which can be handled by a separate component.
 */
public class DeadLetterQueueEvent extends ApplicationEvent {

    private final Object originalEvent;
    private final Map<String, Object> originalHeaders;
    private final Throwable error;
    private final String deadLetterDestination;
    private final long eventTimestamp;

    public DeadLetterQueueEvent(Object source, Object originalEvent,
                               Map<String, Object> originalHeaders,
                               Throwable error, String deadLetterDestination) {
        super(source);
        this.originalEvent = originalEvent;
        this.originalHeaders = originalHeaders;
        this.error = error;
        this.deadLetterDestination = deadLetterDestination;
        this.eventTimestamp = System.currentTimeMillis();
    }

    // Getters
    public Object getOriginalEvent() { return originalEvent; }
    public Map<String, Object> getOriginalHeaders() { return originalHeaders; }
    public Throwable getError() { return error; }
    public String getDeadLetterDestination() { return deadLetterDestination; }
    public long getEventTimestamp() { return eventTimestamp; }

    /**
     * Creates enhanced headers for the dead letter queue message.
     */
    public Map<String, Object> createDeadLetterHeaders() {
        Map<String, Object> dlqHeaders = new java.util.HashMap<>(originalHeaders);
        dlqHeaders.put("dlq_reason", "listener_error");
        dlqHeaders.put("dlq_error_message", error.getMessage());
        dlqHeaders.put("dlq_error_class", error.getClass().getName());
        dlqHeaders.put("dlq_timestamp", eventTimestamp);
        dlqHeaders.put("dlq_original_destination", originalHeaders.get("destination"));
        return dlqHeaders;
    }
}
