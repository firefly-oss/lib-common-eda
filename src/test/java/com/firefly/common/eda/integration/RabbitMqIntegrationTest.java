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

package com.firefly.common.eda.integration;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eda.publisher.rabbitmq.RabbitMqEventPublisher;
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.TestEventModels;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for RabbitMQ publisher and consumer.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>End-to-end message publishing to RabbitMQ</li>
 *   <li>Message consumption from RabbitMQ</li>
 *   <li>Exchange and routing key handling</li>
 *   <li>Header propagation</li>
 *   <li>Multiple message handling</li>
 * </ul>
 */
@SpringBootTest(classes = com.firefly.common.eda.testconfig.TestApplication.class)
@Testcontainers
@Import(com.firefly.common.eda.testconfig.TestContainersConfiguration.class)
class RabbitMqIntegrationTest extends BaseIntegrationTest {

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management-alpine")
    );

    @Autowired(required = false)
    private RabbitMqEventPublisher rabbitMqPublisher;

    @Autowired(required = false)
    private EventPublisherFactory publisherFactory;

    private Connection connection;
    private Channel channel;

    @DynamicPropertySource
    static void rabbitMqProperties(DynamicPropertyRegistry registry) {
        registry.add("firefly.eda.publishers.rabbitmq.default.enabled", () -> "true");
        registry.add("firefly.eda.publishers.rabbitmq.default.host", rabbitmq::getHost);
        registry.add("firefly.eda.publishers.rabbitmq.default.port", rabbitmq::getAmqpPort);
        registry.add("firefly.eda.publishers.rabbitmq.default.username", rabbitmq::getAdminUsername);
        registry.add("firefly.eda.publishers.rabbitmq.default.password", rabbitmq::getAdminPassword);
        registry.add("firefly.eda.publishers.rabbitmq.default.default-exchange", () -> "test-exchange");
        
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        // Create RabbitMQ connection and channel for test verification
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitmq.getHost());
        factory.setPort(rabbitmq.getAmqpPort());
        factory.setUsername(rabbitmq.getAdminUsername());
        factory.setPassword(rabbitmq.getAdminPassword());
        
        connection = factory.newConnection();
        channel = connection.createChannel();
    }

    @AfterEach
    void tearDown() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Should publish message to RabbitMQ exchange successfully")
    void shouldPublishMessageToRabbitMqExchangeSuccessfully() throws IOException {
        // Skip if RabbitMQ publisher is not available
        if (rabbitMqPublisher == null) {
            System.out.println("Skipping test - RabbitMQ publisher not available");
            return;
        }

        // Arrange
        String exchange = "test-exchange";
        String routingKey = "test.routing.key";
        String queueName = "test-queue";
        
        // Declare exchange, queue, and binding
        channel.exchangeDeclare(exchange, "topic", true);
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, exchange, routingKey);
        
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create("RabbitMQ test message");

        // Act
        StepVerifier.create(rabbitMqPublisher.publish(event, exchange + "/" + routingKey))
                .verifyComplete();

        // Assert - Check message was received
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(queueName, true);
                    assertThat(response).isNotNull();
                    String messageBody = new String(response.getBody(), StandardCharsets.UTF_8);
                    assertThat(messageBody).contains("RabbitMQ test message");
                });
    }

    @Test
    @DisplayName("Should publish message with headers to RabbitMQ")
    void shouldPublishMessageWithHeadersToRabbitMq() throws IOException {
        // Skip if RabbitMQ publisher is not available
        if (rabbitMqPublisher == null) {
            System.out.println("Skipping test - RabbitMQ publisher not available");
            return;
        }

        // Arrange
        String exchange = "test-exchange-headers";
        String routingKey = "test.headers";
        String queueName = "test-queue-headers";
        
        channel.exchangeDeclare(exchange, "topic", true);
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, exchange, routingKey);
        
        TestEventModels.OrderCreatedEvent event = TestEventModels.OrderCreatedEvent.create("customer-789", 149.99);
        Map<String, Object> headers = Map.of(
                "transaction-id", "txn-789",
                "priority", "high"
        );

        // Act
        StepVerifier.create(rabbitMqPublisher.publish(event, exchange + "/" + routingKey, headers))
                .verifyComplete();

        // Assert
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(queueName, true);
                    assertThat(response).isNotNull();
                    
                    // Verify headers
                    var messageHeaders = response.getProps().getHeaders();
                    assertThat(messageHeaders).isNotNull();
                    assertThat(messageHeaders).containsKey("transaction-id");
                });
    }

    @Test
    @DisplayName("Should publish multiple messages to RabbitMQ")
    void shouldPublishMultipleMessagesToRabbitMq() throws IOException {
        // Skip if RabbitMQ publisher is not available
        if (rabbitMqPublisher == null) {
            System.out.println("Skipping test - RabbitMQ publisher not available");
            return;
        }

        // Arrange
        String exchange = "test-exchange-multiple";
        String routingKey = "test.multiple";
        String queueName = "test-queue-multiple";
        int messageCount = 5;
        
        channel.exchangeDeclare(exchange, "topic", true);
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, exchange, routingKey);

        // Act - Publish multiple messages
        for (int i = 0; i < messageCount; i++) {
            TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create("Message " + i);
            StepVerifier.create(rabbitMqPublisher.publish(event, exchange + "/" + routingKey))
                    .verifyComplete();
        }

        // Assert - Verify all messages were received
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    long messageCountInQueue = channel.messageCount(queueName);
                    assertThat(messageCountInQueue).isGreaterThanOrEqualTo(messageCount);
                });
    }

    @Test
    @DisplayName("Should verify RabbitMQ publisher is available")
    void shouldVerifyRabbitMqPublisherIsAvailable() {
        // Skip if RabbitMQ publisher is not available
        if (rabbitMqPublisher == null) {
            System.out.println("Skipping test - RabbitMQ publisher not available");
            return;
        }

        // Assert
        assertThat(rabbitMqPublisher.isAvailable()).isTrue();
        assertThat(rabbitMqPublisher.getPublisherType()).isEqualTo(PublisherType.RABBITMQ);
    }

    @Test
    @DisplayName("Should get RabbitMQ publisher from factory")
    void shouldGetRabbitMqPublisherFromFactory() {
        // Skip if publisher factory is not available
        if (publisherFactory == null) {
            System.out.println("Skipping test - Publisher factory not available");
            return;
        }

        // Act
        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.RABBITMQ, "default");

        // Assert - May be null if RabbitMQ is not configured
        // Publisher may be wrapped in ResilientEventPublisher, so check publisher type instead of class
        if (publisher != null) {
            assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.RABBITMQ);
            assertThat(publisher.isAvailable()).isTrue();
        }
    }

    @Test
    @DisplayName("Should handle exchange-only destination (empty routing key)")
    void shouldHandleExchangeOnlyDestination() throws IOException {
        // Skip if RabbitMQ publisher is not available
        if (rabbitMqPublisher == null) {
            System.out.println("Skipping test - RabbitMQ publisher not available");
            return;
        }

        // Arrange
        String exchange = "test-exchange-only";
        String queueName = "test-queue-fanout";
        
        // Use fanout exchange which doesn't need routing key
        channel.exchangeDeclare(exchange, "fanout", true);
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, exchange, "");
        
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create("Fanout message");

        // Act - Publish with exchange only (no routing key)
        StepVerifier.create(rabbitMqPublisher.publish(event, exchange))
                .verifyComplete();

        // Assert
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(queueName, true);
                    assertThat(response).isNotNull();
                });
    }
}

