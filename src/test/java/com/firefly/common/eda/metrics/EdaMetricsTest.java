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

package com.firefly.common.eda.metrics;

import com.firefly.common.eda.testconfig.TestEventModels.SimpleTestEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EDA metrics functionality.
 * <p>
 * Tests metrics collection, recording, and reporting for event publishing,
 * consumption, and error handling scenarios.
 */
@ExtendWith(MockitoExtension.class)
class EdaMetricsTest {

    private MeterRegistry meterRegistry;
    private EdaMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new EdaMetricsCollector(meterRegistry);
    }

    @Test
    void shouldRecordEventPublishedMetrics() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        String destination = "test-topic";
        String publisherType = "kafka";

        // Act
        metricsCollector.recordEventPublished(event, destination, publisherType);

        // Assert
        Counter counter = meterRegistry.find("eda.events.published")
            .tag("event.type", "SimpleTestEvent")
            .tag("destination", destination)
            .tag("publisher.type", publisherType)
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordEventConsumedMetrics() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        String source = "test-topic";
        String consumerType = "kafka";

        // Act
        metricsCollector.recordEventConsumed(event, source, consumerType);

        // Assert
        Counter counter = meterRegistry.find("eda.events.consumed")
            .tag("event.type", "SimpleTestEvent")
            .tag("source", source)
            .tag("consumer.type", consumerType)
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordEventProcessingTime() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        String listenerMethod = "TestService.handleEvent";
        Duration processingTime = Duration.ofMillis(150);

        // Act
        metricsCollector.recordEventProcessingTime(event, listenerMethod, processingTime);

        // Assert
        Timer timer = meterRegistry.find("eda.events.processing.time")
            .tag("event.type", "SimpleTestEvent")
            .tag("listener.method", listenerMethod)
            .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150.0);
    }

    @Test
    void shouldRecordEventErrorMetrics() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        String listenerMethod = "TestService.handleEvent";
        String errorType = "RuntimeException";
        String errorStrategy = "LOG_AND_CONTINUE";

        // Act
        metricsCollector.recordEventError(event, listenerMethod, errorType, errorStrategy);

        // Assert
        Counter counter = meterRegistry.find("eda.events.errors")
            .tag("event.type", "SimpleTestEvent")
            .tag("listener.method", listenerMethod)
            .tag("error.type", errorType)
            .tag("error.strategy", errorStrategy)
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordPublisherHealthMetrics() {
        // Arrange
        String publisherType = "kafka";
        String connectionId = "primary";
        boolean isHealthy = true;

        // Act
        metricsCollector.recordPublisherHealth(publisherType, connectionId, isHealthy);

        // Assert
        Counter counter = meterRegistry.find("eda.publisher.health")
            .tag("publisher.type", publisherType)
            .tag("connection.id", connectionId)
            .tag("status", "healthy")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordConsumerHealthMetrics() {
        // Arrange
        String consumerType = "rabbitmq";
        String connectionId = "secondary";
        boolean isHealthy = false;

        // Act
        metricsCollector.recordConsumerHealth(consumerType, connectionId, isHealthy);

        // Assert
        Counter counter = meterRegistry.find("eda.consumer.health")
            .tag("consumer.type", consumerType)
            .tag("connection.id", connectionId)
            .tag("status", "unhealthy")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordRetryMetrics() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        String listenerMethod = "TestService.handleEvent";
        int attemptNumber = 2;
        boolean successful = false;

        // Act
        metricsCollector.recordRetryAttempt(event, listenerMethod, attemptNumber, successful);

        // Assert
        Counter counter = meterRegistry.find("eda.events.retries")
            .tag("event.type", "SimpleTestEvent")
            .tag("listener.method", listenerMethod)
            .tag("attempt", "2")
            .tag("successful", "false")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordDeadLetterQueueMetrics() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        String originalDestination = "orders-topic";
        String dlqDestination = "orders-dlq";
        String reason = "max_retries_exceeded";

        // Act
        metricsCollector.recordDeadLetterQueue(event, originalDestination, dlqDestination, reason);

        // Assert
        Counter counter = meterRegistry.find("eda.events.dead_letter_queue")
            .tag("event.type", "SimpleTestEvent")
            .tag("original.destination", originalDestination)
            .tag("dlq.destination", dlqDestination)
            .tag("reason", reason)
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordMultipleEventsCorrectly() {
        // Arrange
        SimpleTestEvent event1 = new SimpleTestEvent("id-1", "message 1", Instant.now());
        SimpleTestEvent event2 = new SimpleTestEvent("id-2", "message 2", Instant.now());

        // Act
        metricsCollector.recordEventPublished(event1, "topic-1", "kafka");
        metricsCollector.recordEventPublished(event2, "topic-1", "kafka");
        metricsCollector.recordEventPublished(event1, "topic-2", "rabbitmq");

        // Assert
        Counter kafkaCounter = meterRegistry.find("eda.events.published")
            .tag("destination", "topic-1")
            .tag("publisher.type", "kafka")
            .counter();

        Counter rabbitmqCounter = meterRegistry.find("eda.events.published")
            .tag("destination", "topic-2")
            .tag("publisher.type", "rabbitmq")
            .counter();

        assertThat(kafkaCounter.count()).isEqualTo(2.0);
        assertThat(rabbitmqCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldProvideMetricsSummary() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        
        // Record various metrics
        metricsCollector.recordEventPublished(event, "test-topic", "kafka");
        metricsCollector.recordEventConsumed(event, "test-topic", "kafka");
        metricsCollector.recordEventError(event, "TestService.handle", "RuntimeException", "LOG_AND_CONTINUE");

        // Act
        Map<String, Object> summary = metricsCollector.getMetricsSummary();

        // Assert
        assertThat(summary).isNotEmpty();
        assertThat(summary).containsKey("events.published.total");
        assertThat(summary).containsKey("events.consumed.total");
        assertThat(summary).containsKey("events.errors.total");
    }

    @Test
    void shouldHandleNullEventGracefully() {
        // Act & Assert - Should not throw exception
        metricsCollector.recordEventPublished(null, "test-topic", "kafka");
        metricsCollector.recordEventConsumed(null, "test-topic", "kafka");
        metricsCollector.recordEventError(null, "TestService.handle", "RuntimeException", "LOG_AND_CONTINUE");

        // Verify metrics are recorded with "unknown" event type
        Counter publishedCounter = meterRegistry.find("eda.events.published")
            .tag("event.type", "unknown")
            .counter();

        assertThat(publishedCounter).isNotNull();
        assertThat(publishedCounter.count()).isEqualTo(1.0);
    }

    /**
     * Simple metrics collector implementation for testing.
     */
    static class EdaMetricsCollector {
        private final MeterRegistry meterRegistry;

        public EdaMetricsCollector(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        public void recordEventPublished(Object event, String destination, String publisherType) {
            String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
            Counter.builder("eda.events.published")
                .tag("event.type", eventType)
                .tag("destination", destination)
                .tag("publisher.type", publisherType)
                .register(meterRegistry)
                .increment();
        }

        public void recordEventConsumed(Object event, String source, String consumerType) {
            String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
            Counter.builder("eda.events.consumed")
                .tag("event.type", eventType)
                .tag("source", source)
                .tag("consumer.type", consumerType)
                .register(meterRegistry)
                .increment();
        }

        public void recordEventProcessingTime(Object event, String listenerMethod, Duration processingTime) {
            String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
            Timer.builder("eda.events.processing.time")
                .tag("event.type", eventType)
                .tag("listener.method", listenerMethod)
                .register(meterRegistry)
                .record(processingTime);
        }

        public void recordEventError(Object event, String listenerMethod, String errorType, String errorStrategy) {
            String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
            Counter.builder("eda.events.errors")
                .tag("event.type", eventType)
                .tag("listener.method", listenerMethod)
                .tag("error.type", errorType)
                .tag("error.strategy", errorStrategy)
                .register(meterRegistry)
                .increment();
        }

        public void recordPublisherHealth(String publisherType, String connectionId, boolean isHealthy) {
            Counter.builder("eda.publisher.health")
                .tag("publisher.type", publisherType)
                .tag("connection.id", connectionId)
                .tag("status", isHealthy ? "healthy" : "unhealthy")
                .register(meterRegistry)
                .increment();
        }

        public void recordConsumerHealth(String consumerType, String connectionId, boolean isHealthy) {
            Counter.builder("eda.consumer.health")
                .tag("consumer.type", consumerType)
                .tag("connection.id", connectionId)
                .tag("status", isHealthy ? "healthy" : "unhealthy")
                .register(meterRegistry)
                .increment();
        }

        public void recordRetryAttempt(Object event, String listenerMethod, int attemptNumber, boolean successful) {
            String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
            Counter.builder("eda.events.retries")
                .tag("event.type", eventType)
                .tag("listener.method", listenerMethod)
                .tag("attempt", String.valueOf(attemptNumber))
                .tag("successful", String.valueOf(successful))
                .register(meterRegistry)
                .increment();
        }

        public void recordDeadLetterQueue(Object event, String originalDestination, String dlqDestination, String reason) {
            String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
            Counter.builder("eda.events.dead_letter_queue")
                .tag("event.type", eventType)
                .tag("original.destination", originalDestination)
                .tag("dlq.destination", dlqDestination)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
        }

        public Map<String, Object> getMetricsSummary() {
            return Map.of(
                "events.published.total", meterRegistry.find("eda.events.published").counters().stream().mapToDouble(Counter::count).sum(),
                "events.consumed.total", meterRegistry.find("eda.events.consumed").counters().stream().mapToDouble(Counter::count).sum(),
                "events.errors.total", meterRegistry.find("eda.events.errors").counters().stream().mapToDouble(Counter::count).sum()
            );
        }
    }
}
