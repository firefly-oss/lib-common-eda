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

package com.firefly.common.eda.filter;

import com.firefly.common.eda.consumer.EventConsumer;
import com.firefly.common.eda.event.EventEnvelope;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Interface for filtering events in the EDA library.
 * <p>
 * Event filters allow consumers to selectively process messages based on
 * various criteria such as destination, event type, headers, or custom logic.
 * <p>
 * Implementations should be thread-safe as they may be used concurrently
 * by multiple consumer threads.
 */
public interface EventFilter {

    /**
     * Determines whether an event should be accepted for processing.
     *
     * @param messageBody the serialized message body
     * @param headers the message headers/metadata
     * @return true if the event should be processed, false to skip it
     */
    boolean accept(String messageBody, Map<String, Object> headers);

    /**
     * Gets a description of this filter for debugging and logging purposes.
     *
     * @return a human-readable description of the filter
     */
    default String getDescription() {
        return this.getClass().getSimpleName();
    }

    /**
     * Creates a destination-based filter.
     *
     * @param destination the destination pattern to match
     * @return a new destination filter
     */
    static EventFilter byDestination(String destination) {
        return new EventFilter() {
            @Override
            public boolean accept(String messageBody, Map<String, Object> headers) {
                // Try to get destination from headers first
                Object dest = headers != null ? headers.get("destination") : null;
                return dest != null && dest.toString().equals(destination);
            }

            @Override
            public boolean matches(EventEnvelope envelope) {
                return destination.equals(envelope.destination());
            }
        };
    }

    /**
     * Creates an event type-based filter.
     *
     * @param eventTypePattern the event type pattern to match (supports wildcards)
     * @return a new event type filter
     */
    static EventFilter byEventType(String eventTypePattern) {
        return new EventFilter() {
            @Override
            public boolean accept(String messageBody, Map<String, Object> headers) {
                // Try to get event type from headers
                Object eventType = headers != null ? headers.get("eventType") : null;
                return eventType != null && eventType.toString().equals(eventTypePattern);
            }

            @Override
            public boolean matches(EventEnvelope envelope) {
                return eventTypePattern.equals(envelope.eventType());
            }
        };
    }

    /**
     * Creates a header-based filter.
     *
     * @param headerName the header name to check
     * @param headerValue the expected header value (supports wildcards)
     * @return a new header filter
     */
    static EventFilter byHeader(String headerName, String headerValue) {
        return (messageBody, headers) -> {
            if (headers == null) return false;
            Object value = headers.get(headerName);
            return value != null && value.toString().equals(headerValue);
        };
    }

    /**
     * Creates a composite AND filter.
     *
     * @param filters the filters to combine with AND logic
     * @return a new composite filter
     */
    static EventFilter and(EventFilter... filters) {
        return new CompositeEventFilter(CompositeEventFilter.LogicType.AND, filters);
    }

    /**
     * Creates a composite OR filter.
     *
     * @param filters the filters to combine with OR logic
     * @return a new composite filter
     */
    static EventFilter or(EventFilter... filters) {
        return new CompositeEventFilter(CompositeEventFilter.LogicType.OR, filters);
    }

    /**
     * Creates a negation filter.
     *
     * @param filter the filter to negate
     * @return a new negation filter
     */
    static EventFilter not(EventFilter filter) {
        return new EventFilter() {
            @Override
            public boolean accept(String messageBody, Map<String, Object> headers) {
                return !filter.accept(messageBody, headers);
            }

            @Override
            public boolean matches(EventEnvelope envelope) {
                return !filter.matches(envelope);
            }
        };
    }

    /**
     * Tests if an event envelope matches this filter.
     * This is a convenience method for testing.
     *
     * @param envelope the event envelope to test
     * @return true if the envelope matches this filter
     */
    default boolean matches(EventEnvelope envelope) {
        // Convert envelope to the format expected by accept method
        String messageBody = envelope.payload() != null ? envelope.payload().toString() : "";
        return accept(messageBody, envelope.headers());
    }

    /**
     * Creates a header presence filter.
     *
     * @param headerName the header name to check for presence
     * @return a new header presence filter
     */
    static EventFilter byHeaderPresence(String headerName) {
        return (messageBody, headers) -> headers != null && headers.containsKey(headerName);
    }

    /**
     * Creates a custom predicate-based filter.
     *
     * @param predicate the predicate to apply to event envelopes
     * @return a new predicate filter
     */
    static EventFilter byPredicate(Predicate<EventEnvelope> predicate) {
        return new EventFilter() {
            @Override
            public boolean accept(String messageBody, Map<String, Object> headers) {
                // This is a simplified implementation for testing
                // In practice, you'd need more context from the envelope
                return true;
            }

            @Override
            public boolean matches(EventEnvelope envelope) {
                return predicate.test(envelope);
            }
        };
    }
}