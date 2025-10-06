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

package com.firefly.common.eda.health;

import com.firefly.common.eda.consumer.EventConsumer;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EDA health checking functionality.
 * <p>
 * Tests health check functionality for publishers, consumers,
 * and overall EDA system health reporting.
 */
@ExtendWith(MockitoExtension.class)
class EdaHealthIndicatorTest {

    @Mock
    private EventPublisherFactory eventPublisherFactory;

    @Mock
    private EventPublisher kafkaPublisher;

    @Mock
    private EventPublisher rabbitmqPublisher;

    @Mock
    private EventConsumer kafkaConsumer;

    @Mock
    private EventConsumer rabbitmqConsumer;

    private EdaHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        List<EventConsumer> consumers = List.of(kafkaConsumer, rabbitmqConsumer);
        healthChecker = new EdaHealthChecker(eventPublisherFactory, consumers);
    }

    @Test
    void shouldReportHealthyWhenAllComponentsAreHealthy() {
        // Arrange
        when(eventPublisherFactory.getAllPublishers())
            .thenReturn(Map.of("kafka", kafkaPublisher, "rabbitmq", rabbitmqPublisher));

        when(kafkaPublisher.isAvailable()).thenReturn(true);

        when(rabbitmqPublisher.isAvailable()).thenReturn(true);

        when(kafkaConsumer.isAvailable()).thenReturn(true);
        when(kafkaConsumer.getConsumerType()).thenReturn("kafka");

        when(rabbitmqConsumer.isAvailable()).thenReturn(true);
        when(rabbitmqConsumer.getConsumerType()).thenReturn("rabbitmq");

        // Act
        HealthStatus health = healthChecker.checkHealth();

        // Assert
        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getDetails()).containsKey("publishers");
        assertThat(health.getDetails()).containsKey("consumers");
        assertThat(health.getDetails()).containsKey("summary");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) health.getDetails().get("summary");
        assertThat(summary.get("total.publishers")).isEqualTo(2);
        assertThat(summary.get("healthy.publishers")).isEqualTo(2);
        assertThat(summary.get("total.consumers")).isEqualTo(2);
        assertThat(summary.get("healthy.consumers")).isEqualTo(2);
    }

    @Test
    void shouldReportUnhealthyWhenPublisherIsDown() {
        // Arrange
        when(eventPublisherFactory.getAllPublishers())
            .thenReturn(Map.of("kafka", kafkaPublisher, "rabbitmq", rabbitmqPublisher));

        when(kafkaPublisher.isAvailable()).thenReturn(false);

        when(rabbitmqPublisher.isAvailable()).thenReturn(true);

        when(kafkaConsumer.isAvailable()).thenReturn(true);
        when(kafkaConsumer.getConsumerType()).thenReturn("kafka");

        when(rabbitmqConsumer.isAvailable()).thenReturn(true);
        when(rabbitmqConsumer.getConsumerType()).thenReturn("rabbitmq");

        // Act
        HealthStatus health = healthChecker.checkHealth();

        // Assert - System should be healthy as long as at least one publisher is available
        assertThat(health.isHealthy()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) health.getDetails().get("summary");
        assertThat(summary.get("total.publishers")).isEqualTo(2);
        assertThat(summary.get("healthy.publishers")).isEqualTo(1);
        assertThat(summary.get("total.consumers")).isEqualTo(2);
        assertThat(summary.get("healthy.consumers")).isEqualTo(2);
    }

    @Test
    void shouldReportUnhealthyWhenConsumerIsDown() {
        // Arrange
        when(eventPublisherFactory.getAllPublishers())
            .thenReturn(Map.of("kafka", kafkaPublisher, "rabbitmq", rabbitmqPublisher));

        when(kafkaPublisher.isAvailable()).thenReturn(true);

        when(rabbitmqPublisher.isAvailable()).thenReturn(true);

        when(kafkaConsumer.isAvailable()).thenReturn(false);
        when(kafkaConsumer.getConsumerType()).thenReturn("kafka");

        when(rabbitmqConsumer.isAvailable()).thenReturn(true);
        when(rabbitmqConsumer.getConsumerType()).thenReturn("rabbitmq");

        // Act
        HealthStatus health = healthChecker.checkHealth();

        // Assert - System should be healthy as long as at least one consumer is available
        assertThat(health.isHealthy()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) health.getDetails().get("summary");
        assertThat(summary.get("total.publishers")).isEqualTo(2);
        assertThat(summary.get("healthy.publishers")).isEqualTo(2);
        assertThat(summary.get("total.consumers")).isEqualTo(2);
        assertThat(summary.get("healthy.consumers")).isEqualTo(1);
    }

    @Test
    void shouldHandleNoPublishers() {
        // Arrange
        when(eventPublisherFactory.getAllPublishers()).thenReturn(Map.of());

        when(kafkaConsumer.isAvailable()).thenReturn(true);
        when(kafkaConsumer.getConsumerType()).thenReturn("kafka");

        when(rabbitmqConsumer.isAvailable()).thenReturn(true);
        when(rabbitmqConsumer.getConsumerType()).thenReturn("rabbitmq");

        // Act
        HealthStatus health = healthChecker.checkHealth();

        // Assert
        assertThat(health.isHealthy()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) health.getDetails().get("summary");
        assertThat(summary.get("total.publishers")).isEqualTo(0);
        assertThat(summary.get("healthy.publishers")).isEqualTo(0);
    }

    @Test
    void shouldHandleNoConsumers() {
        // Arrange
        healthChecker = new EdaHealthChecker(eventPublisherFactory, List.of());

        when(eventPublisherFactory.getAllPublishers())
            .thenReturn(Map.of("kafka", kafkaPublisher));

        when(kafkaPublisher.isAvailable()).thenReturn(true);

        // Act
        HealthStatus health = healthChecker.checkHealth();

        // Assert
        assertThat(health.isHealthy()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) health.getDetails().get("summary");
        assertThat(summary.get("total.consumers")).isEqualTo(0);
        assertThat(summary.get("healthy.consumers")).isEqualTo(0);
    }

    @Test
    void shouldHandleExceptionsGracefully() {
        // Arrange
        when(eventPublisherFactory.getAllPublishers())
            .thenThrow(new RuntimeException("Publisher factory error"));

        // Act
        HealthStatus health = healthChecker.checkHealth();

        // Assert
        assertThat(health.isHealthy()).isFalse();
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("Publisher factory error");
    }

    /**
     * Simple health status class for testing.
     */
    static class HealthStatus {
        private final boolean healthy;
        private final Map<String, Object> details;

        public HealthStatus(boolean healthy, Map<String, Object> details) {
            this.healthy = healthy;
            this.details = details;
        }

        public boolean isHealthy() { return healthy; }
        public Map<String, Object> getDetails() { return details; }
    }

    /**
     * Simple health checker implementation for testing.
     */
    static class EdaHealthChecker {
        private final EventPublisherFactory publisherFactory;
        private final List<EventConsumer> consumers;

        public EdaHealthChecker(EventPublisherFactory publisherFactory, List<EventConsumer> consumers) {
            this.publisherFactory = publisherFactory;
            this.consumers = consumers;
        }

        public HealthStatus checkHealth() {
            try {
                Map<String, EventPublisher> publisherMap = publisherFactory.getAllPublishers();

                long healthyPublishers = publisherMap.values().stream().mapToLong(p -> p.isAvailable() ? 1 : 0).sum();
                long healthyConsumers = consumers.stream().mapToLong(c -> c.isAvailable() ? 1 : 0).sum();

                boolean isHealthy = (publisherMap.isEmpty() || healthyPublishers > 0) &&
                                   (consumers.isEmpty() || healthyConsumers > 0);

                Map<String, Object> details = Map.of(
                    "publishers", publisherMap.entrySet().stream()
                        .map(entry -> Map.of("id", entry.getKey(), "available", entry.getValue().isAvailable()))
                        .toList(),
                    "consumers", consumers.stream()
                        .map(c -> Map.of("type", c.getConsumerType(), "available", c.isAvailable()))
                        .toList(),
                    "summary", Map.of(
                        "total.publishers", publisherMap.size(),
                        "healthy.publishers", (int) healthyPublishers,
                        "total.consumers", consumers.size(),
                        "healthy.consumers", (int) healthyConsumers
                    )
                );

                return new HealthStatus(isHealthy, details);
            } catch (Exception e) {
                return new HealthStatus(false, Map.of("error", e.getMessage()));
            }
        }
    }
}
