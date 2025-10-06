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
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for edge cases in dynamic topic selection functionality.
 * <p>
 * This test class focuses on edge cases, error conditions, and boundary scenarios
 * that might not be covered in the main integration tests.
 */
@DisplayName("Dynamic Topic Selection Edge Cases")
class DynamicTopicEdgeCasesTest extends BaseIntegrationTest {

    @Autowired
    private EventPublisherFactory publisherFactory;

    @Test
    @DisplayName("Should handle null custom destination gracefully")
    void shouldHandleNullCustomDestinationGracefully() {
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, null);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo("application-events");
    }

    @Test
    @DisplayName("Should handle empty string custom destination gracefully")
    void shouldHandleEmptyStringCustomDestinationGracefully() {
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "");
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo("application-events");
    }

    @Test
    @DisplayName("Should handle whitespace-only custom destination gracefully")
    void shouldHandleWhitespaceOnlyCustomDestinationGracefully() {
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "   \t\n  ");
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo("application-events");
    }

    @Test
    @DisplayName("Should handle very long custom destination names")
    void shouldHandleVeryLongCustomDestinationNames() {
        // Arrange
        String longDestination = "a".repeat(1000); // 1000 character destination
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, longDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(longDestination);
    }

    @Test
    @DisplayName("Should handle special characters in custom destination names")
    void shouldHandleSpecialCharactersInCustomDestinationNames() {
        // Arrange
        String specialDestination = "test-topic_with.special@chars#123$%^&*()";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, specialDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(specialDestination);
    }

    @Test
    @DisplayName("Should handle Unicode characters in custom destination names")
    void shouldHandleUnicodeCharactersInCustomDestinationNames() {
        // Arrange
        String unicodeDestination = "测试主题-тест-テスト-🚀";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, unicodeDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(unicodeDestination);
    }

    @Test
    @DisplayName("Should return null for unavailable publisher types")
    void shouldReturnNullForUnavailablePublisherTypes() {
        // Act - KAFKA and RABBITMQ are not available in test profile
        EventPublisher kafkaPublisher = publisherFactory.getPublisherWithDestination(
                PublisherType.KAFKA, "test-topic");
        EventPublisher rabbitPublisher = publisherFactory.getPublisherWithDestination(
                PublisherType.RABBITMQ, "test-exchange");
        
        // Assert
        assertThat(kafkaPublisher).isNull();
        assertThat(rabbitPublisher).isNull();
    }

    @Test
    @DisplayName("Should handle destination resolution with null explicit destination")
    void shouldHandleDestinationResolutionWithNullExplicitDestination() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "custom-default");
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create(
                "test@example.com", "testuser");
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, null))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle destination resolution with empty string explicit destination")
    void shouldHandleDestinationResolutionWithEmptyStringExplicitDestination() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "custom-default");
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create(
                "test@example.com", "testuser");
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, ""))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle destination resolution with whitespace explicit destination")
    void shouldHandleDestinationResolutionWithWhitespaceExplicitDestination() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "custom-default");
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create(
                "test@example.com", "testuser");
        
        // Act & Assert
        StepVerifier.create(publisher.publish(event, "   "))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle concurrent access to publisher factory")
    void shouldHandleConcurrentAccessToPublisherFactory() {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        EventPublisher[] publishers = new EventPublisher[threadCount];
        
        // Act
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                publishers[index] = publisherFactory.getPublisherWithDestination(
                        PublisherType.APPLICATION_EVENT, "concurrent-test-" + index);
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Assert
        for (int i = 0; i < threadCount; i++) {
            assertThat(publishers[i]).isNotNull();
            assertThat(publishers[i].getDefaultDestination()).isEqualTo("concurrent-test-" + i);
        }
    }

    @Test
    @DisplayName("Should handle publisher health checks with custom destinations")
    void shouldHandlePublisherHealthChecksWithCustomDestinations() {
        // Arrange
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "health-test-destination");
        
        // Act & Assert
        StepVerifier.create(publisher.getHealth())
                .assertNext(health -> {
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getDetails()).containsKey("customDefaultDestination");
                    assertThat(health.getDetails()).containsKey("effectiveDefaultDestination");
                    assertThat(health.getDetails().get("customDefaultDestination")).isEqualTo("health-test-destination");
                    assertThat(health.getDetails().get("effectiveDefaultDestination")).isEqualTo("health-test-destination");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle publisher caching correctly")
    void shouldHandlePublisherCachingCorrectly() {
        // Act
        EventPublisher publisher1 = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "cache-test");
        EventPublisher publisher2 = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "cache-test");
        EventPublisher publisher3 = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "different-destination");
        
        // Assert
        // Publishers with same destination should be different instances (not cached)
        // because they are wrapped with DestinationAwarePublisher
        assertThat(publisher1).isNotSameAs(publisher2);
        assertThat(publisher1).isNotSameAs(publisher3);
        assertThat(publisher2).isNotSameAs(publisher3);
        
        // But they should have the same default destination
        assertThat(publisher1.getDefaultDestination()).isEqualTo(publisher2.getDefaultDestination());
        assertThat(publisher1.getDefaultDestination()).isNotEqualTo(publisher3.getDefaultDestination());
    }
}
