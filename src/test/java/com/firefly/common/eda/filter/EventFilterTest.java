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

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.event.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventFilter implementations.
 */
@DisplayName("Event Filter Tests")
class EventFilterTest {

    private static final EventEnvelope.AckCallback NOOP_ACK = new EventEnvelope.AckCallback() {
        @Override
        public Mono<Void> acknowledge() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> reject(Throwable error) {
            return Mono.empty();
        }
    };

    @Test
    @DisplayName("Should filter by destination")
    void shouldFilterByDestination() {
        // Arrange
        EventFilter filter = EventFilter.byDestination("test-destination");
        EventEnvelope matchingEnvelope = createEnvelope("test-destination", "TestEvent", "test-data");
        EventEnvelope nonMatchingEnvelope = createEnvelope("other-destination", "TestEvent", "test-data");

        // Act & Assert
        assertThat(filter.matches(matchingEnvelope)).isTrue();
        assertThat(filter.matches(nonMatchingEnvelope)).isFalse();
    }

    @Test
    @DisplayName("Should filter by event type")
    void shouldFilterByEventType() {
        // Arrange
        EventFilter filter = EventFilter.byEventType("UserCreated");
        EventEnvelope matchingEnvelope = createEnvelope("test-destination", "UserCreated", "test-data");
        EventEnvelope nonMatchingEnvelope = createEnvelope("test-destination", "OrderCreated", "test-data");

        // Act & Assert
        assertThat(filter.matches(matchingEnvelope)).isTrue();
        assertThat(filter.matches(nonMatchingEnvelope)).isFalse();
    }

    @Test
    @DisplayName("Should filter by header value")
    void shouldFilterByHeaderValue() {
        // Arrange
        EventFilter filter = EventFilter.byHeader("priority", "high");
        
        Map<String, Object> matchingHeaders = new HashMap<>();
        matchingHeaders.put("priority", "high");
        EventEnvelope matchingEnvelope = createEnvelopeWithHeaders("test-destination", "TestEvent", "test-data", matchingHeaders);
        
        Map<String, Object> nonMatchingHeaders = new HashMap<>();
        nonMatchingHeaders.put("priority", "low");
        EventEnvelope nonMatchingEnvelope = createEnvelopeWithHeaders("test-destination", "TestEvent", "test-data", nonMatchingHeaders);

        // Act & Assert
        assertThat(filter.matches(matchingEnvelope)).isTrue();
        assertThat(filter.matches(nonMatchingEnvelope)).isFalse();
    }

    @Test
    @DisplayName("Should filter by custom predicate")
    void shouldFilterByCustomPredicate() {
        // Arrange
        EventFilter filter = EventFilter.byPredicate(envelope -> 
            envelope.destination().startsWith("test-")
        );
        
        EventEnvelope matchingEnvelope = createEnvelope("test-destination", "TestEvent", "test-data");
        EventEnvelope nonMatchingEnvelope = createEnvelope("prod-destination", "TestEvent", "test-data");

        // Act & Assert
        assertThat(filter.matches(matchingEnvelope)).isTrue();
        assertThat(filter.matches(nonMatchingEnvelope)).isFalse();
    }

    @Test
    @DisplayName("Should combine filters with AND logic")
    void shouldCombineFiltersWithAndLogic() {
        // Arrange
        EventFilter destinationFilter = EventFilter.byDestination("test-destination");
        EventFilter eventTypeFilter = EventFilter.byEventType("UserCreated");
        EventFilter combinedFilter = EventFilter.and(destinationFilter, eventTypeFilter);

        EventEnvelope matchingEnvelope = createEnvelope("test-destination", "UserCreated", "test-data");
        EventEnvelope nonMatchingEnvelope1 = createEnvelope("other-destination", "UserCreated", "test-data");
        EventEnvelope nonMatchingEnvelope2 = createEnvelope("test-destination", "OrderCreated", "test-data");

        // Act & Assert
        assertThat(combinedFilter.matches(matchingEnvelope)).isTrue();
        assertThat(combinedFilter.matches(nonMatchingEnvelope1)).isFalse();
        assertThat(combinedFilter.matches(nonMatchingEnvelope2)).isFalse();
    }

    @Test
    @DisplayName("Should combine filters with OR logic")
    void shouldCombineFiltersWithOrLogic() {
        // Arrange
        EventFilter filter1 = EventFilter.byEventType("UserCreated");
        EventFilter filter2 = EventFilter.byEventType("OrderCreated");
        EventFilter combinedFilter = EventFilter.or(filter1, filter2);

        EventEnvelope matchingEnvelope1 = createEnvelope("test-destination", "UserCreated", "test-data");
        EventEnvelope matchingEnvelope2 = createEnvelope("test-destination", "OrderCreated", "test-data");
        EventEnvelope nonMatchingEnvelope = createEnvelope("test-destination", "ProductCreated", "test-data");

        // Act & Assert
        assertThat(combinedFilter.matches(matchingEnvelope1)).isTrue();
        assertThat(combinedFilter.matches(matchingEnvelope2)).isTrue();
        assertThat(combinedFilter.matches(nonMatchingEnvelope)).isFalse();
    }

    @Test
    @DisplayName("Should negate filter")
    void shouldNegateFilter() {
        // Arrange
        EventFilter filter = EventFilter.byEventType("UserCreated");
        EventFilter negatedFilter = EventFilter.not(filter);

        EventEnvelope matchingEnvelope = createEnvelope("test-destination", "UserCreated", "test-data");
        EventEnvelope nonMatchingEnvelope = createEnvelope("test-destination", "OrderCreated", "test-data");

        // Act & Assert
        assertThat(negatedFilter.matches(matchingEnvelope)).isFalse();
        assertThat(negatedFilter.matches(nonMatchingEnvelope)).isTrue();
    }

    @Test
    @DisplayName("Should accept message body and headers")
    void shouldAcceptMessageBodyAndHeaders() {
        // Arrange
        EventFilter filter = EventFilter.byHeader("priority", "high");
        Map<String, Object> headers = new HashMap<>();
        headers.put("priority", "high");

        // Act & Assert
        assertThat(filter.accept("test-body", headers)).isTrue();
        
        headers.put("priority", "low");
        assertThat(filter.accept("test-body", headers)).isFalse();
    }

    @Test
    @DisplayName("Should handle null headers gracefully")
    void shouldHandleNullHeadersGracefully() {
        // Arrange
        EventFilter filter = EventFilter.byHeader("priority", "high");

        // Act & Assert
        assertThat(filter.accept("test-body", null)).isFalse();
    }

    @Test
    @DisplayName("Should handle missing header gracefully")
    void shouldHandleMissingHeaderGracefully() {
        // Arrange
        EventFilter filter = EventFilter.byHeader("priority", "high");
        Map<String, Object> headers = new HashMap<>();
        headers.put("other-header", "value");

        // Act & Assert
        assertThat(filter.accept("test-body", headers)).isFalse();
    }

    // Helper methods

    private EventEnvelope createEnvelope(String destination, String eventType, Object payload) {
        return EventEnvelope.forConsuming(
                destination,
                eventType,
                payload,
                "test-tx-id",
                new HashMap<>(),
                EventEnvelope.EventMetadata.empty(),
                Instant.now(),
                "KAFKA",
                "default",
                NOOP_ACK
        );
    }

    private EventEnvelope createEnvelopeWithHeaders(String destination, String eventType, Object payload, Map<String, Object> headers) {
        return EventEnvelope.forConsuming(
                destination,
                eventType,
                payload,
                "test-tx-id",
                headers,
                EventEnvelope.EventMetadata.empty(),
                Instant.now(),
                "KAFKA",
                "default",
                NOOP_ACK
        );
    }
}

