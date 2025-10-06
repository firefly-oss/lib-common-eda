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

package com.firefly.common.eda.properties;

import com.firefly.common.eda.annotation.PublisherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EdaProperties configuration.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>Default values are correctly applied</li>
 *   <li>Custom properties are loaded correctly</li>
 *   <li>Validation constraints work as expected</li>
 *   <li>Nested configuration objects are properly initialized</li>
 * </ul>
 */
@SpringBootTest(classes = com.firefly.common.eda.testconfig.TestApplication.class)
@ActiveProfiles("test")
class EdaPropertiesTest {

    @Autowired
    private EdaProperties edaProperties;

    @Test
    @DisplayName("Should load default properties when no custom configuration provided")
    void shouldLoadDefaultProperties() {
        // Assert - Verify default values
        assertThat(edaProperties.isEnabled()).isTrue();
        assertThat(edaProperties.getDefaultPublisherType()).isEqualTo(PublisherType.AUTO);
        assertThat(edaProperties.getDefaultConnectionId()).isEqualTo("default");
        assertThat(edaProperties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(edaProperties.isMetricsEnabled()).isTrue();
        assertThat(edaProperties.isHealthEnabled()).isTrue();
        assertThat(edaProperties.isTracingEnabled()).isTrue();
        assertThat(edaProperties.getDefaultSerializationFormat()).isEqualTo("json");
    }

    @Test
    @DisplayName("Should have publishers configuration initialized with defaults")
    void shouldHavePublishersConfigurationInitialized() {
        // Assert - Verify publishers configuration exists
        assertThat(edaProperties.getPublishers()).isNotNull();
        
        // Verify application event publisher defaults
        assertThat(edaProperties.getPublishers().getApplicationEvent()).isNotNull();
        assertThat(edaProperties.getPublishers().getApplicationEvent().isEnabled()).isTrue();
        assertThat(edaProperties.getPublishers().getApplicationEvent().getDefaultDestination())
                .isEqualTo("application-events");
        
        // Verify Kafka defaults
        assertThat(edaProperties.getPublishers().getKafka()).isNotNull();
        assertThat(edaProperties.getPublishers().getKafka()).containsKey("default");
        assertThat(edaProperties.getPublishers().getKafka().get("default").isEnabled()).isTrue();
        assertThat(edaProperties.getPublishers().getKafka().get("default").getDefaultTopic())
                .isEqualTo("events");
        
        // Verify RabbitMQ defaults
        assertThat(edaProperties.getPublishers().getRabbitmq()).isNotNull();
        assertThat(edaProperties.getPublishers().getRabbitmq()).containsKey("default");
        assertThat(edaProperties.getPublishers().getRabbitmq().get("default").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should have resilience configuration initialized with defaults")
    void shouldHaveResilienceConfigurationInitialized() {
        // Assert - Verify resilience configuration exists
        assertThat(edaProperties.getResilience()).isNotNull();
        assertThat(edaProperties.getResilience().isEnabled()).isTrue();
        
        // Verify circuit breaker defaults
        assertThat(edaProperties.getResilience().getCircuitBreaker()).isNotNull();
        assertThat(edaProperties.getResilience().getCircuitBreaker().isEnabled()).isTrue();
        
        // Verify retry defaults
        assertThat(edaProperties.getResilience().getRetry()).isNotNull();
        assertThat(edaProperties.getResilience().getRetry().isEnabled()).isTrue();
        
        // Verify rate limiter defaults (disabled by default)
        assertThat(edaProperties.getResilience().getRateLimiter()).isNotNull();
        assertThat(edaProperties.getResilience().getRateLimiter().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should retrieve publisher config by type and connection ID")
    void shouldRetrievePublisherConfigByTypeAndConnectionId() {
        // Act & Assert - Verify getPublisherConfig method
        Object kafkaConfig = edaProperties.getPublisherConfig(PublisherType.KAFKA, "default");
        assertThat(kafkaConfig).isNotNull();
        assertThat(kafkaConfig).isInstanceOf(EdaProperties.Publishers.KafkaConfig.class);

        Object rabbitmqConfig = edaProperties.getPublisherConfig(PublisherType.RABBITMQ, "default");
        assertThat(rabbitmqConfig).isNotNull();
        assertThat(rabbitmqConfig).isInstanceOf(EdaProperties.Publishers.RabbitMqConfig.class);

        Object appEventConfig = edaProperties.getPublisherConfig(PublisherType.APPLICATION_EVENT, null);
        assertThat(appEventConfig).isNotNull();
        assertThat(appEventConfig).isInstanceOf(EdaProperties.Publishers.ApplicationEvent.class);
    }

    @Test
    @DisplayName("Should return null for non-existent connection ID")
    void shouldReturnNullForNonExistentConnectionId() {
        // Act
        Object config = edaProperties.getPublisherConfig(PublisherType.KAFKA, "non-existent");
        
        // Assert
        assertThat(config).isNull();
    }

    /**
     * Test with custom properties loaded from test-specific configuration.
     */
    @SpringBootTest(classes = com.firefly.common.eda.testconfig.TestApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "firefly.eda.enabled=false",
            "firefly.eda.default-publisher-type=KAFKA",
            "firefly.eda.default-connection-id=custom",
            "firefly.eda.default-timeout=60s",
            "firefly.eda.metrics-enabled=false",
            "firefly.eda.health-enabled=false",
            "firefly.eda.publishers.kafka.default.bootstrap-servers=localhost:9092",
            "firefly.eda.publishers.kafka.default.default-topic=custom-topic",
            "firefly.eda.publishers.rabbitmq.default.host=localhost",
            "firefly.eda.publishers.rabbitmq.default.port=5672",
            "firefly.eda.resilience.enabled=false"
    })
    static class CustomPropertiesTest {

        @Autowired
        private EdaProperties edaProperties;

        @Test
        @DisplayName("Should load custom properties correctly")
        void shouldLoadCustomProperties() {
            // Assert - Verify custom values
            assertThat(edaProperties.isEnabled()).isFalse();
            assertThat(edaProperties.getDefaultPublisherType()).isEqualTo(PublisherType.KAFKA);
            assertThat(edaProperties.getDefaultConnectionId()).isEqualTo("custom");
            assertThat(edaProperties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(edaProperties.isMetricsEnabled()).isFalse();
            assertThat(edaProperties.isHealthEnabled()).isFalse();
            
            // Verify Kafka custom properties
            assertThat(edaProperties.getPublishers().getKafka().get("default").getBootstrapServers())
                    .isEqualTo("localhost:9092");
            assertThat(edaProperties.getPublishers().getKafka().get("default").getDefaultTopic())
                    .isEqualTo("custom-topic");
            
            // Verify RabbitMQ custom properties
            assertThat(edaProperties.getPublishers().getRabbitmq().get("default").getHost())
                    .isEqualTo("localhost");
            assertThat(edaProperties.getPublishers().getRabbitmq().get("default").getPort())
                    .isEqualTo(5672);
            
            // Verify resilience custom properties
            assertThat(edaProperties.getResilience().isEnabled()).isFalse();
        }
    }
}

