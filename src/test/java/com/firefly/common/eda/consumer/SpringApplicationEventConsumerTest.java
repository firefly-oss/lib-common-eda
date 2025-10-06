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

package com.firefly.common.eda.consumer;

import com.firefly.common.eda.event.EventEnvelope;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SpringApplicationEventConsumer.
 */
@DisplayName("SpringApplicationEventConsumer Tests")
class SpringApplicationEventConsumerTest {

    private SpringApplicationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SpringApplicationEventConsumer();
    }

    @Test
    @DisplayName("Should start and stop successfully")
    void shouldStartAndStopSuccessfully() {
        // Initially not running
        assertThat(consumer.isRunning()).isFalse();

        // Start
        StepVerifier.create(consumer.start())
                .verifyComplete();
        assertThat(consumer.isRunning()).isTrue();

        // Stop
        StepVerifier.create(consumer.stop())
                .verifyComplete();
        assertThat(consumer.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should always be available")
    void shouldAlwaysBeAvailable() {
        assertThat(consumer.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should return correct consumer type")
    void shouldReturnCorrectConsumerType() {
        assertThat(consumer.getConsumerType()).isEqualTo("APPLICATION_EVENT");
    }

    @Test
    @DisplayName("Should return health information")
    void shouldReturnHealthInformation() {
        StepVerifier.create(consumer.getHealth())
                .assertNext(health -> {
                    assertThat(health.getConsumerType()).isEqualTo("APPLICATION_EVENT");
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getDetails()).containsKey("description");
                    assertThat(health.getDetails()).containsKey("suitable_for");
                    assertThat(health.getDetails()).containsKey("event_sink_subscribers");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should receive and emit events when running")
    void shouldReceiveAndEmitEventsWhenRunning() {
        // Start consumer
        consumer.start().block();

        // Create test event
        TestEventModels.SimpleTestEvent testEvent = new TestEventModels.SimpleTestEvent(
                "test-id", "test message", Instant.now());
        EventEnvelope envelope = EventEnvelope.forConsuming(
                "test-destination",
                "SimpleTestEvent",
                testEvent,
                "APPLICATION_EVENT",
                null
        );

        // Subscribe to consumer flux
        StepVerifier.create(consumer.consume().take(1))
                .then(() -> {
                    // Simulate Spring publishing the event
                    consumer.onApplicationEvent(envelope);
                })
                .assertNext(receivedEnvelope -> {
                    assertThat(receivedEnvelope.destination()).isEqualTo("test-destination");
                    assertThat(receivedEnvelope.eventType()).isEqualTo("SimpleTestEvent");
                    assertThat(receivedEnvelope.payload()).isEqualTo(testEvent);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should ignore events when not running")
    void shouldIgnoreEventsWhenNotRunning() {
        // Don't start consumer (should be stopped by default)
        assertThat(consumer.isRunning()).isFalse();

        // Create test event
        TestEventModels.SimpleTestEvent testEvent = new TestEventModels.SimpleTestEvent(
                "test-id", "test message", Instant.now());
        EventEnvelope envelope = EventEnvelope.forConsuming(
                "test-destination",
                "SimpleTestEvent",
                testEvent,
                "APPLICATION_EVENT",
                null
        );

        // Simulate Spring publishing the event when consumer is not running
        consumer.onApplicationEvent(envelope);

        // Subscribe to consumer flux with timeout - should not receive the event
        StepVerifier.create(consumer.consume().take(1).timeout(Duration.ofMillis(500)))
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("Should filter events by destination")
    void shouldFilterEventsByDestination() {
        // Start consumer
        consumer.start().block();

        // Create test events with different destinations
        TestEventModels.SimpleTestEvent testEvent1 = new TestEventModels.SimpleTestEvent(
                "test-id-1", "test message 1", Instant.now());
        EventEnvelope envelope1 = EventEnvelope.forConsuming(
                "destination-1",
                "SimpleTestEvent",
                testEvent1,
                "APPLICATION_EVENT",
                null
        );

        TestEventModels.SimpleTestEvent testEvent2 = new TestEventModels.SimpleTestEvent(
                "test-id-2", "test message 2", Instant.now());
        EventEnvelope envelope2 = EventEnvelope.forConsuming(
                "destination-2",
                "SimpleTestEvent",
                testEvent2,
                "APPLICATION_EVENT",
                null
        );

        // Subscribe to consumer flux with destination filter
        StepVerifier.create(consumer.consume("destination-1").take(1))
                .then(() -> {
                    // Publish both events
                    consumer.onApplicationEvent(envelope2); // Should be filtered out
                    consumer.onApplicationEvent(envelope1); // Should pass through
                })
                .assertNext(receivedEnvelope -> {
                    assertThat(receivedEnvelope.destination()).isEqualTo("destination-1");
                    assertThat(receivedEnvelope.payload()).isEqualTo(testEvent1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle multiple subscribers")
    void shouldHandleMultipleSubscribers() {
        // Start consumer
        consumer.start().block();

        // Create test event
        TestEventModels.SimpleTestEvent testEvent = new TestEventModels.SimpleTestEvent(
                "test-id", "test message", Instant.now());
        EventEnvelope envelope = EventEnvelope.forConsuming(
                "test-destination",
                "SimpleTestEvent",
                testEvent,
                "APPLICATION_EVENT",
                null
        );

        // Create two subscribers and verify they both receive the event
        StepVerifier.create(consumer.consume().take(1))
                .then(() -> consumer.onApplicationEvent(envelope))
                .assertNext(receivedEnvelope -> {
                    assertThat(receivedEnvelope.destination()).isEqualTo("test-destination");
                    assertThat(receivedEnvelope.payload()).isEqualTo(testEvent);
                })
                .verifyComplete();

        StepVerifier.create(consumer.consume().take(1))
                .then(() -> consumer.onApplicationEvent(envelope))
                .assertNext(receivedEnvelope -> {
                    assertThat(receivedEnvelope.destination()).isEqualTo("test-destination");
                    assertThat(receivedEnvelope.payload()).isEqualTo(testEvent);
                })
                .verifyComplete();
    }
}
