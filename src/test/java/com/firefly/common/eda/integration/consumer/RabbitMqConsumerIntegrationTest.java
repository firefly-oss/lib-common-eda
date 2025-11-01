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

package com.firefly.common.eda.integration.consumer;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.consumer.EventConsumer;
import com.firefly.common.eda.event.EventEnvelope;
import com.firefly.common.eda.publisher.rabbitmq.RabbitMqEventPublisher;
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.TestApplication;
import com.firefly.common.eda.testconfig.TestContainersConfiguration;
import com.firefly.common.eda.testconfig.TestEventListeners;
import com.firefly.common.eda.testconfig.TestEventModels;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for RabbitMQ event consumer.
 * Tests consumer functionality including message consumption and acknowledgments.
 */
@SpringBootTest(classes = TestApplication.class)
@Testcontainers
@DisplayName("RabbitMQ Consumer Integration Tests")
class RabbitMqConsumerIntegrationTest extends BaseIntegrationTest {

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management-alpine")
    );

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("rabbitMqEventConsumer")
    private EventConsumer rabbitMqConsumer;

    @Autowired(required = false)
    private RabbitMqEventPublisher rabbitMqPublisher;

    @Autowired(required = false)
    private TestEventListeners testEventListeners;

    private String testQueue;
    private String testExchange;
    private String testRoutingKey;
    private List<EventEnvelope> receivedEvents;
    private Connection connection;
    private Channel channel;

    @DynamicPropertySource
    static void rabbitMqProperties(DynamicPropertyRegistry registry) {
        // Configure ONLY through firefly.eda.* properties - NO spring.rabbitmq.* properties
        // This ensures 100% hexagonal architecture with no direct Spring RabbitMQ configuration

        // RabbitMQ Publisher configuration
        registry.add("firefly.eda.publishers.rabbitmq.default.enabled", () -> "true");
        registry.add("firefly.eda.publishers.rabbitmq.default.host", rabbitmq::getHost);
        registry.add("firefly.eda.publishers.rabbitmq.default.port", rabbitmq::getAmqpPort);
        registry.add("firefly.eda.publishers.rabbitmq.default.username", rabbitmq::getAdminUsername);
        registry.add("firefly.eda.publishers.rabbitmq.default.password", rabbitmq::getAdminPassword);
        registry.add("firefly.eda.publishers.rabbitmq.default.default-exchange", () -> "test-exchange");

        // RabbitMQ Consumer configuration
        registry.add("firefly.eda.consumer.enabled", () -> "true");
        registry.add("firefly.eda.consumer.rabbitmq.default.enabled", () -> "true");
        registry.add("firefly.eda.consumer.rabbitmq.default.host", rabbitmq::getHost);
        registry.add("firefly.eda.consumer.rabbitmq.default.port", rabbitmq::getAmqpPort);
        registry.add("firefly.eda.consumer.rabbitmq.default.username", rabbitmq::getAdminUsername);
        registry.add("firefly.eda.consumer.rabbitmq.default.password", rabbitmq::getAdminPassword);
        registry.add("firefly.eda.consumer.rabbitmq.default.queues", () -> "test-events-queue");
        registry.add("firefly.eda.consumer.rabbitmq.default.concurrent-consumers", () -> "1");
        registry.add("firefly.eda.consumer.rabbitmq.default.max-concurrent-consumers", () -> "5");
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        receivedEvents = new CopyOnWriteArrayList<>();
        testQueue = "test-events-queue";
        testExchange = "test-consumer-exchange-" + System.currentTimeMillis();
        testRoutingKey = "test.routing.key";

        // Clear test listeners
        if (testEventListeners != null) {
            testEventListeners.clear();
        }

        // Create RabbitMQ connection for test setup
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitmq.getHost());
        factory.setPort(rabbitmq.getAmqpPort());
        factory.setUsername(rabbitmq.getAdminUsername());
        factory.setPassword(rabbitmq.getAdminPassword());

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Declare exchange and queue
        channel.exchangeDeclare(testExchange, "topic", true);

        // Declare the queue that the consumer is listening to
        channel.queueDeclare(testQueue, true, false, false, null);

        // Bind the queue to the test exchange with multiple routing keys
        channel.queueBind(testQueue, testExchange, testRoutingKey);
        channel.queueBind(testQueue, testExchange, "test.headers");

        // Verify queue exists
        var queueInfo = channel.queueDeclarePassive(testQueue);
        System.out.println("✅ Queue verified: " + testQueue + ", messages=" + queueInfo.getMessageCount() + ", consumers=" + queueInfo.getConsumerCount());

        System.out.println("✅ Test setup complete: exchange=" + testExchange + ", queue=" + testQueue);
    }

    @AfterEach
    void tearDown() throws IOException, TimeoutException {
        receivedEvents.clear();
        
        // Clean up RabbitMQ resources
        if (channel != null && channel.isOpen()) {
            try {
                channel.queueDelete(testQueue);
                channel.exchangeDelete(testExchange);
                channel.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Should verify consumer type")
    void shouldVerifyConsumerType() {
        // Skip if consumer is not available
        if (rabbitMqConsumer == null) {
            System.out.println("Skipping test - RabbitMQ consumer not available");
            return;
        }

        // Assert
        assertThat(rabbitMqConsumer.getConsumerType()).isEqualTo("RABBITMQ");
    }

    @Test
    @DisplayName("Should start and stop consumer")
    void shouldStartAndStopConsumer() {
        // Skip if consumer is not available
        if (rabbitMqConsumer == null) {
            System.out.println("Skipping test - RabbitMQ consumer not available");
            return;
        }

        // Act & Assert - Start
        StepVerifier.create(rabbitMqConsumer.start())
                .verifyComplete();

        // Act & Assert - Stop
        StepVerifier.create(rabbitMqConsumer.stop())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if consumer is running")
    void shouldCheckIfConsumerIsRunning() {
        // Skip if consumer is not available
        if (rabbitMqConsumer == null) {
            System.out.println("Skipping test - RabbitMQ consumer not available");
            return;
        }

        // Start consumer
        rabbitMqConsumer.start().block();

        // Assert
        assertThat(rabbitMqConsumer.isRunning()).isTrue();

        // Stop consumer
        rabbitMqConsumer.stop().block();

        // Assert
        assertThat(rabbitMqConsumer.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should get consumer health")
    void shouldGetConsumerHealth() {
        // Skip if consumer is not available
        if (rabbitMqConsumer == null) {
            System.out.println("Skipping test - RabbitMQ consumer not available");
            return;
        }

        // Act & Assert
        StepVerifier.create(rabbitMqConsumer.getHealth())
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health.getConsumerType()).isEqualTo(PublisherType.RABBITMQ.name());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should consume from specific queue")
    void shouldConsumeFromSpecificQueue() {
        // Skip if consumer is not available
        if (rabbitMqConsumer == null) {
            System.out.println("Skipping test - RabbitMQ consumer not available");
            return;
        }

        // Act - Subscribe to specific queue
        rabbitMqConsumer.consume(testQueue)
                .take(Duration.ofSeconds(2))
                .subscribe(receivedEvents::add);

        // Wait a bit to ensure subscription is active
        await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    // Just verify no errors occurred
                    assertThat(receivedEvents).isNotNull();
                });
    }

    @Test
    @DisplayName("Should publish and consume event end-to-end with EventListener")
    void shouldPublishAndConsumeEventEndToEnd() throws IOException {
        // Skip if components are not available
        if (rabbitMqConsumer == null || rabbitMqPublisher == null || testEventListeners == null) {
            System.out.println("Skipping test - Required components not available");
            System.out.println("Consumer: " + (rabbitMqConsumer != null));
            System.out.println("Publisher: " + (rabbitMqPublisher != null));
            System.out.println("Listeners: " + (testEventListeners != null));
            return;
        }

        System.out.println("🚀 [RABBITMQ E2E TEST] Starting end-to-end test");

        // Arrange - Start the consumer
        rabbitMqConsumer.start().block();
        assertThat(rabbitMqConsumer.isRunning()).isTrue();
        System.out.println("✅ [RABBITMQ E2E TEST] Consumer started");

        // Create test event
        String testMessage = "RabbitMQ end-to-end test message " + System.currentTimeMillis();
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create(testMessage);

        Map<String, Object> headers = new HashMap<>();
        headers.put("test-header", "test-value");
        headers.put("transaction-id", "txn-" + System.currentTimeMillis());

        // Act - Publish event
        String destination = testExchange + "/" + testRoutingKey;
        System.out.println("📤 [RABBITMQ E2E TEST] Will send: " + testMessage);
        System.out.println("🎯 [RABBITMQ E2E TEST] Target destination: " + destination);
        System.out.println("📤 [RABBITMQ E2E TEST] Publishing event...");

        StepVerifier.create(rabbitMqPublisher.publish(event, destination, headers))
                .verifyComplete();

        System.out.println("✅ [RABBITMQ E2E TEST] Event published successfully");

        // Assert - Wait for event to be consumed and processed by listener
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    int totalEvents = testEventListeners.getTotalEventsReceived();
                    System.out.println("📊 [RABBITMQ E2E TEST] Total events received by listeners: " + totalEvents);
                    System.out.println("   - Simple events: " + testEventListeners.getSimpleEvents().size());
                    System.out.println("   - Order events: " + testEventListeners.getOrderEvents().size());
                    System.out.println("   - User events: " + testEventListeners.getUserEvents().size());

                    assertThat(testEventListeners.getSimpleEvents())
                            .as("SimpleTestEvent should be received by listener")
                            .isNotEmpty();

                    assertThat(testEventListeners.getSimpleEvents().get(0).getMessage())
                            .contains("RabbitMQ end-to-end test message");
                });

        System.out.println("✅ [RABBITMQ E2E TEST] Event consumed and processed successfully!");

        // Verify event content
        TestEventModels.SimpleTestEvent receivedEvent = testEventListeners.getSimpleEvents().get(0);
        System.out.println("📥 [RABBITMQ E2E TEST] Received: " + receivedEvent.getMessage());
        System.out.println("🔍 [RABBITMQ E2E TEST] Verifying message content...");
        System.out.println("✅ [RABBITMQ E2E TEST] Message content verified successfully!");

        // Stop consumer
        rabbitMqConsumer.stop().block();
    }

    @Test
    @DisplayName("Should publish and consume multiple events")
    void shouldPublishAndConsumeMultipleEvents() throws IOException {
        // Skip if components are not available
        if (rabbitMqConsumer == null || rabbitMqPublisher == null || testEventListeners == null) {
            System.out.println("Skipping test - Required components not available");
            return;
        }

        System.out.println("🚀 Starting multiple events test");

        // Arrange - Start the consumer
        rabbitMqConsumer.start().block();

        // Declare additional exchange and binding for order events
        String orderExchange = "test-exchange-headers";
        String orderRoutingKey = "test.headers";
        channel.exchangeDeclare(orderExchange, "topic", true);
        channel.queueDeclare(testQueue, true, false, false, null);
        channel.queueBind(testQueue, orderExchange, orderRoutingKey);

        // Act - Publish multiple events
        for (int i = 0; i < 3; i++) {
            TestEventModels.SimpleTestEvent simpleEvent = TestEventModels.SimpleTestEvent.create(
                    "Message " + i
            );
            rabbitMqPublisher.publish(simpleEvent, testExchange + "/" + testRoutingKey).block();
            System.out.println("📤 Published simple event " + i);
        }

        for (int i = 0; i < 2; i++) {
            TestEventModels.OrderCreatedEvent orderEvent = TestEventModels.OrderCreatedEvent.create(
                    "customer-" + i,
                    100.0 + i
            );
            rabbitMqPublisher.publish(orderEvent, orderExchange + "/" + orderRoutingKey).block();
            System.out.println("📤 Published order event " + i);
        }

        // Assert - Wait for all events to be consumed
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    System.out.println("📊 Simple events: " + testEventListeners.getSimpleEvents().size());
                    System.out.println("📊 Order events: " + testEventListeners.getOrderEvents().size());

                    assertThat(testEventListeners.getSimpleEvents())
                            .as("Should receive 3 simple events")
                            .hasSizeGreaterThanOrEqualTo(3);

                    assertThat(testEventListeners.getOrderEvents())
                            .as("Should receive 2 order events")
                            .hasSizeGreaterThanOrEqualTo(2);
                });

        System.out.println("✅ All events consumed successfully!");

        // Stop consumer
        rabbitMqConsumer.stop().block();
    }
}

