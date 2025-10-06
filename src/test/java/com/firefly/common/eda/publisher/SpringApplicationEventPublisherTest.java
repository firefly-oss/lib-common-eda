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

package com.firefly.common.eda.publisher;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.event.EventEnvelope;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SpringApplicationEventPublisher.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>Events are published correctly</li>
 *   <li>Headers and metadata are preserved</li>
 *   <li>Publisher availability checks work</li>
 *   <li>Health checks return correct status</li>
 * </ul>
 */
@SpringBootTest(classes = {
        com.firefly.common.eda.testconfig.TestApplication.class,
        SpringApplicationEventPublisherTest.TestConfig.class
})
@ActiveProfiles("test")
class SpringApplicationEventPublisherTest {

    @Autowired
    private SpringApplicationEventPublisher publisher;

    @Autowired
    private TestEventListener testEventListener;

    @BeforeEach
    void setUp() {
        // Clear events from previous tests to ensure test isolation
        testEventListener.clear();
    }

    @Test
    @DisplayName("Should publish event successfully")
    void shouldPublishEventSuccessfully() {
        // Arrange
        String testMessage = "SpringApplicationEvent test message " + System.currentTimeMillis();
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create(testMessage);
        String destination = "test-destination";

        System.out.println("🚀 [SPRING E2E TEST] Starting SpringApplicationEvent test");
        System.out.println("📤 [SPRING E2E TEST] Will send: " + testMessage);
        System.out.println("🎯 [SPRING E2E TEST] Target destination: " + destination);

        // Act
        System.out.println("📤 [SPRING E2E TEST] Publishing event...");
        StepVerifier.create(publisher.publish(event, destination))
                .verifyComplete();
        System.out.println("✅ [SPRING E2E TEST] Event published successfully");

        // Assert
        assertThat(testEventListener.getReceivedEvents()).hasSize(1);
        var receivedEvent = testEventListener.getReceivedEvents().poll();
        assertThat(receivedEvent).isNotNull();
        assertThat(receivedEvent.destination()).isEqualTo(destination);
        assertThat(receivedEvent.payload()).isEqualTo(event);

        System.out.println("📥 [SPRING E2E TEST] Received: " + ((TestEventModels.SimpleTestEvent)receivedEvent.payload()).getMessage());
        System.out.println("🔍 [SPRING E2E TEST] Verifying message content...");
        System.out.println("✅ [SPRING E2E TEST] Message content verified successfully!");
    }

    @Test
    @DisplayName("Should publish event with headers")
    void shouldPublishEventWithHeaders() {
        // Arrange
        String testMessage = "SpringApplicationEvent with headers test " + System.currentTimeMillis();
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create(testMessage);
        String destination = "test-destination-headers";
        Map<String, Object> headers = Map.of(
                "transaction-id", "txn-123",
                "custom-header", "custom-value"
        );

        System.out.println("🚀 [SPRING HEADERS TEST] Starting SpringApplicationEvent headers test");
        System.out.println("📤 [SPRING HEADERS TEST] Will send: " + testMessage);
        System.out.println("🎯 [SPRING HEADERS TEST] Target destination: " + destination);
        System.out.println("📋 [SPRING HEADERS TEST] Headers: " + headers);

        // Act
        System.out.println("📤 [SPRING HEADERS TEST] Publishing event with headers...");
        StepVerifier.create(publisher.publish(event, destination, headers))
                .verifyComplete();
        System.out.println("✅ [SPRING HEADERS TEST] Event published successfully");

        // Assert
        assertThat(testEventListener.getReceivedEvents()).hasSize(1);
        var receivedEvent = testEventListener.getReceivedEvents().poll();
        assertThat(receivedEvent).isNotNull();
        assertThat(receivedEvent.transactionId()).isEqualTo("txn-123");
        assertThat(receivedEvent.headers()).containsEntry("custom-header", "custom-value");

        System.out.println("📥 [SPRING HEADERS TEST] Received: " + ((TestEventModels.SimpleTestEvent)receivedEvent.payload()).getMessage());
        System.out.println("📋 [SPRING HEADERS TEST] Received headers: " + receivedEvent.headers());
        System.out.println("🔍 [SPRING HEADERS TEST] Verifying headers...");
        System.out.println("✅ [SPRING HEADERS TEST] Headers verified successfully!");
    }

    @Test
    @DisplayName("Should use default destination when none provided")
    void shouldUseDefaultDestinationWhenNoneProvided() {
        // Arrange
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create("test message");
        
        // Act
        StepVerifier.create(publisher.publish(event, null))
                .verifyComplete();
        
        // Assert
        assertThat(testEventListener.getReceivedEvents()).hasSize(1);
        var receivedEvent = testEventListener.getReceivedEvents().poll();
        assertThat(receivedEvent).isNotNull();
        assertThat(receivedEvent.destination()).isEqualTo("application-events");
    }

    @Test
    @DisplayName("Should report correct publisher type")
    void shouldReportCorrectPublisherType() {
        // Assert
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.APPLICATION_EVENT);
    }

    @Test
    @DisplayName("Should be available when properly configured")
    void shouldBeAvailableWhenProperlyConfigured() {
        // Assert
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should return correct default destination")
    void shouldReturnCorrectDefaultDestination() {
        // Assert
        assertThat(publisher.getDefaultDestination()).isEqualTo("application-events");
    }

    @Test
    @DisplayName("Should return healthy status")
    void shouldReturnHealthyStatus() {
        // Act & Assert
        StepVerifier.create(publisher.getHealth())
                .assertNext(health -> {
                    assertThat(health.getPublisherType()).isEqualTo(PublisherType.APPLICATION_EVENT);
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getStatus()).isEqualTo("UP");
                    assertThat(health.getDetails()).containsEntry("enabled", true);
                    assertThat(health.getDetails()).containsEntry("defaultDestination", "application-events");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should publish multiple events in sequence")
    void shouldPublishMultipleEventsInSequence() {
        // Arrange
        int eventCount = 5;
        
        // Act
        for (int i = 0; i < eventCount; i++) {
            TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create("message-" + i);
            StepVerifier.create(publisher.publish(event, "test-destination"))
                    .verifyComplete();
        }
        
        // Assert
        assertThat(testEventListener.getReceivedEvents()).hasSize(eventCount);
    }

    @Test
    @DisplayName("Should handle different event types")
    void shouldHandleDifferentEventTypes() {
        // Arrange
        TestEventModels.OrderCreatedEvent orderEvent = TestEventModels.OrderCreatedEvent.create("customer-1", 99.99);
        TestEventModels.UserRegisteredEvent userEvent = TestEventModels.UserRegisteredEvent.create(
                "user@example.com", 
                "testuser"
        );
        
        // Act
        StepVerifier.create(publisher.publish(orderEvent, "orders"))
                .verifyComplete();
        StepVerifier.create(publisher.publish(userEvent, "users"))
                .verifyComplete();
        
        // Assert
        assertThat(testEventListener.getReceivedEvents()).hasSize(2);
    }

    /**
     * Test configuration with event listener.
     */
    @Configuration
    static class TestConfig {
        
        @Bean
        public TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    /**
     * Test event listener to capture published events.
     */
    @Component
    static class TestEventListener {

        private final ConcurrentLinkedQueue<EventEnvelope> receivedEvents = new ConcurrentLinkedQueue<>();
        private final AtomicInteger eventCount = new AtomicInteger(0);

        @EventListener
        public void onApplicationEvent(EventEnvelope event) {
            receivedEvents.add(event);
            eventCount.incrementAndGet();
        }

        public ConcurrentLinkedQueue<EventEnvelope> getReceivedEvents() {
            return receivedEvents;
        }

        public int getEventCount() {
            return eventCount.get();
        }

        public void clear() {
            receivedEvents.clear();
            eventCount.set(0);
        }
    }
}

