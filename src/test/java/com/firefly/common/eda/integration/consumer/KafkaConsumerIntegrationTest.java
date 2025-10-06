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
import com.firefly.common.eda.publisher.kafka.KafkaEventPublisher;
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.TestApplication;
import com.firefly.common.eda.testconfig.TestEventListeners;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Kafka event consumer.
 * Tests consumer functionality including filtering, acknowledgments, and error handling.
 */
@SpringBootTest(classes = TestApplication.class)
@Testcontainers
@DisplayName("Kafka Consumer Integration Tests")
class KafkaConsumerIntegrationTest extends BaseIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    ).withReuse(true);

    @Autowired(required = false)
    private EventConsumer kafkaConsumer;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaPublisher;

    @Autowired(required = false)
    private KafkaAdmin kafkaAdmin;

    @Autowired(required = false)
    private TestEventListeners testEventListeners;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        // Wait for Kafka to be ready
        try {
            kafka.start();
            System.out.println("⏳ Waiting for Kafka to be ready...");
            Thread.sleep(8000); // Give Kafka more time to fully initialize
            System.out.println("✅ Kafka is ready at: " + kafka.getBootstrapServers());
        } catch (Exception e) {
            System.err.println("❌ Error waiting for Kafka: " + e.getMessage());
        }

        // ONLY Firefly EDA properties - NO Spring properties
        registry.add("firefly.eda.publishers.kafka.default.enabled", () -> "true");
        registry.add("firefly.eda.publishers.kafka.default.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("firefly.eda.publishers.kafka.default.default-topic", () -> "events");

        // Consumer configuration - ENABLED with regex pattern
        // Using regex pattern "test-consumer-topic-.*" to match topics like "test-consumer-topic-1", "test-consumer-topic-2", etc.
        registry.add("firefly.eda.consumer.enabled", () -> "true");
        registry.add("firefly.eda.consumers.kafka.default.enabled", () -> "true");
        registry.add("firefly.eda.consumers.kafka.default.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("firefly.eda.consumers.kafka.default.group-id", () -> "firefly-eda");
        registry.add("firefly.eda.consumers.kafka.default.topics", () -> "test-consumer-topic-.*");
        registry.add("firefly.eda.consumers.kafka.default.auto-offset-reset", () -> "earliest");

        // Configure Kafka consumer to update metadata more frequently for pattern subscriptions
        registry.add("spring.kafka.consumer.properties.metadata.max.age.ms", () -> "500");
        registry.add("spring.kafka.consumer.properties.fetch.max.wait.ms", () -> "500");
    }

    private String testTopic;
    private List<EventEnvelope> receivedEvents;

    @BeforeEach
    void setUp() {
        receivedEvents = new CopyOnWriteArrayList<>();
        testTopic = "test-consumer-topic-" + System.currentTimeMillis();

        // Clear test listeners
        if (testEventListeners != null) {
            testEventListeners.clear();
        }

        // Create test topic
        if (kafkaAdmin != null) {
            try {
                AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                NewTopic newTopic = new NewTopic(testTopic, 1, (short) 1);
                adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
                adminClient.close();
                System.out.println("✅ Test setup complete: topic=" + testTopic);

                // Wait for Kafka to propagate topic metadata to consumers
                // This is critical for pattern-based subscriptions to detect the new topic
                Thread.sleep(5000);
                System.out.println("✅ Waited for topic metadata propagation");
            } catch (Exception e) {
                System.out.println("Failed to create topic: " + e.getMessage());
            }
        }
    }

    @AfterEach
    void tearDown() {
        receivedEvents.clear();
    }

    @Test
    @DisplayName("Should consume messages from Kafka topic")
    void shouldConsumeMessagesFromKafkaTopic() {
        // Skip if consumer is not available
        if (kafkaConsumer == null || kafkaPublisher == null) {
            System.out.println("Skipping test - Kafka consumer or publisher not available");
            return;
        }

        // Arrange - Use a unique topic for this test to avoid interference
        String uniqueTestTopic = "test-consumer-topic-flux-" + System.currentTimeMillis();
        String testMessage = "Kafka consumer test message " + System.currentTimeMillis();
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create(testMessage);
        List<EventEnvelope> localReceivedEvents = new ArrayList<>();

        System.out.println("🚀 [KAFKA FLUX TEST] Starting test");
        System.out.println("📤 [KAFKA FLUX TEST] Will send: " + testMessage);
        System.out.println("🎯 [KAFKA FLUX TEST] Target topic: " + uniqueTestTopic);

        // Create the unique topic
        try {
            kafkaAdmin.createOrModifyTopics(new NewTopic(uniqueTestTopic, 1, (short) 1));
            System.out.println("✅ Test setup complete: topic=" + uniqueTestTopic);

            // Wait for topic metadata propagation
            Thread.sleep(3000);
            System.out.println("✅ Waited for topic metadata propagation");
        } catch (Exception e) {
            System.out.println("Failed to create topic: " + e.getMessage());
        }

        // Create a dedicated subscription for this test
        Disposable subscription = kafkaConsumer.consume(uniqueTestTopic)
                .filter(envelope -> envelope.destination().equals(uniqueTestTopic))
                .take(1)
                .doOnNext(envelope -> {
                    System.out.println("📥 [KAFKA FLUX TEST] Received envelope from: " + envelope.destination());
                    System.out.println("📥 [KAFKA FLUX TEST] Received payload: " + envelope.payload());
                    localReceivedEvents.add(envelope);
                })
                .subscribe();

        try {
            // Wait a bit for subscription to be active
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Act - Publish event
            System.out.println("📤 [KAFKA FLUX TEST] Publishing event...");
            StepVerifier.create(kafkaPublisher.publish(event, uniqueTestTopic))
                    .verifyComplete();
            System.out.println("✅ [KAFKA FLUX TEST] Event published successfully");

            // Assert - Wait for message to be consumed
            await().atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        assertThat(localReceivedEvents).hasSizeGreaterThanOrEqualTo(1);
                        EventEnvelope envelope = localReceivedEvents.get(0);
                        assertThat(envelope.destination()).isEqualTo(uniqueTestTopic);
                        // The payload in the flux is the raw JSON string, not the deserialized object
                        assertThat(envelope.payload()).isInstanceOf(String.class);
                        String jsonPayload = (String) envelope.payload();
                        assertThat(jsonPayload).contains(testMessage);
                        assertThat(jsonPayload).contains("message");

                        System.out.println("✅ [KAFKA FLUX TEST] Successfully verified received message");
                        System.out.println("📥 [KAFKA FLUX TEST] Final verification - received: " + jsonPayload);
                    });
        } finally {
            // Clean up subscription
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        }
    }

    @Test
    @DisplayName("Should publish and consume event end-to-end with listeners")
    void shouldPublishAndConsumeEventEndToEnd() {
        // Skip if consumer or publisher is not available
        if (kafkaConsumer == null || kafkaPublisher == null || testEventListeners == null) {
            System.out.println("Skipping test - Kafka consumer, publisher or listeners not available");
            return;
        }

        System.out.println("🚀 [KAFKA E2E TEST] Starting Kafka end-to-end test");

        // Start consumer
        StepVerifier.create(kafkaConsumer.start())
                .verifyComplete();
        System.out.println("✅ [KAFKA E2E TEST] Consumer started");

        // Give additional time for the consumer to discover the new topic
        try {
            Thread.sleep(2000);
            System.out.println("✅ [KAFKA E2E TEST] Additional wait for consumer topic discovery");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create and publish event
        String testMessage = "Kafka end-to-end test message " + System.currentTimeMillis();
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create(testMessage);

        System.out.println("📤 [KAFKA E2E TEST] Will send: " + testMessage);
        System.out.println("🎯 [KAFKA E2E TEST] Target topic: " + testTopic);
        System.out.println("📤 [KAFKA E2E TEST] Publishing event...");

        StepVerifier.create(kafkaPublisher.publish(event, testTopic))
                .verifyComplete();
        System.out.println("✅ [KAFKA E2E TEST] Event published successfully");

        // Wait for listener to receive the event
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    int totalEvents = testEventListeners.getTotalEventsReceived();
                    System.out.println("📊 [KAFKA E2E TEST] Total events received by listeners: " + totalEvents);
                    System.out.println("📊 [KAFKA E2E TEST] Simple events count: " + testEventListeners.getSimpleEvents().size());
                    assertThat(testEventListeners.getSimpleEvents())
                            .as("SimpleTestEvent should be received by listener")
                            .isNotEmpty();
                });

        System.out.println("✅ [KAFKA E2E TEST] Event consumed and processed successfully!");

        // Verify event content
        TestEventModels.SimpleTestEvent receivedEvent = testEventListeners.getSimpleEvents().get(0);
        System.out.println("📥 [KAFKA E2E TEST] Received: " + receivedEvent.getMessage());
        System.out.println("🔍 [KAFKA E2E TEST] Verifying message content...");

        assertThat(receivedEvent.getMessage()).contains("Kafka end-to-end test message");
        System.out.println("✅ [KAFKA E2E TEST] Message content verified successfully!");
    }

    @Test
    @DisplayName("Should verify consumer type")
    void shouldVerifyConsumerType() {
        // Skip if consumer is not available
        if (kafkaConsumer == null) {
            System.out.println("Skipping test - Kafka consumer not available");
            return;
        }

        // Assert
        assertThat(kafkaConsumer.getConsumerType()).isEqualTo("KAFKA");
    }

    @Test
    @DisplayName("Should start and stop consumer")
    void shouldStartAndStopConsumer() {
        // Skip if consumer is not available
        if (kafkaConsumer == null) {
            System.out.println("Skipping test - Kafka consumer not available");
            return;
        }

        // Act & Assert - Start
        StepVerifier.create(kafkaConsumer.start())
                .verifyComplete();

        // Act & Assert - Stop
        StepVerifier.create(kafkaConsumer.stop())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if consumer is running")
    void shouldCheckIfConsumerIsRunning() {
        // Skip if consumer is not available
        if (kafkaConsumer == null) {
            System.out.println("Skipping test - Kafka consumer not available");
            return;
        }

        // Start consumer
        kafkaConsumer.start().block();

        // Assert
        assertThat(kafkaConsumer.isRunning()).isTrue();

        // Stop consumer
        kafkaConsumer.stop().block();

        // Assert
        assertThat(kafkaConsumer.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should get consumer health")
    void shouldGetConsumerHealth() {
        // Skip if consumer is not available
        if (kafkaConsumer == null) {
            System.out.println("Skipping test - Kafka consumer not available");
            return;
        }

        // Act & Assert
        StepVerifier.create(kafkaConsumer.getHealth())
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health.getConsumerType()).isEqualTo(PublisherType.KAFKA.name());
                })
                .verifyComplete();
    }
}

