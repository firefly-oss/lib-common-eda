package com.firefly.common.eda.integration;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.DynamicTopicTestService;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for dynamic topic selection across all messaging platforms.
 * Tests EventPublisher, @PublishResult, and @EventListener with dynamic topics.
 */
@Testcontainers
@DisplayName("Comprehensive Dynamic Topic Selection Tests")
class ComprehensiveDynamicTopicTest extends BaseIntegrationTest {

    @Autowired
    private EventPublisherFactory publisherFactory;

    @Autowired
    private DynamicTopicTestService dynamicTopicTestService;

    @Test
    @DisplayName("Should support dynamic topics with Spring Application Events")
    void shouldSupportDynamicTopicsWithSpringEvents() {
        // Test EventPublisher with custom destination
        EventPublisher customPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "custom-spring-events");
        
        assertThat(customPublisher).isNotNull();
        assertThat(customPublisher.getDefaultDestination()).isEqualTo("custom-spring-events");
        
        // Test publishing with custom default
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create("test@example.com", "testuser");
        
        StepVerifier.create(customPublisher.publish(event, null))
            .verifyComplete();
        
        // Test publishing with explicit destination (should override custom default)
        StepVerifier.create(customPublisher.publish(event, "explicit-spring-destination"))
            .verifyComplete();
        
        // Test fallback to configured default when no custom destination
        EventPublisher defaultPublisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        assertThat(defaultPublisher).isNotNull();

        TestEventModels.UserRegisteredEvent defaultEvent = TestEventModels.UserRegisteredEvent.create("default@example.com", "defaultuser");
        StepVerifier.create(defaultPublisher.publish(defaultEvent, null))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support dynamic topics with Kafka (when available)")
    void shouldSupportDynamicTopicsWithKafka() {
        // Test EventPublisher with custom destination
        EventPublisher customPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA, "custom-kafka-topic");
        
        if (customPublisher != null) {
            assertThat(customPublisher.getDefaultDestination()).isEqualTo("custom-kafka-topic");
            
            TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create("kafka@example.com", "kafkauser");
            
            // Test publishing with custom default
            StepVerifier.create(customPublisher.publish(event, null))
                .verifyComplete();
            
            // Test publishing with explicit destination
            StepVerifier.create(customPublisher.publish(event, "explicit-kafka-topic"))
                .verifyComplete();
        }
        
        // Test fallback to configured default
        EventPublisher defaultPublisher = publisherFactory.getPublisher(PublisherType.KAFKA);
        if (defaultPublisher != null) {
            TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create("kafka-default@example.com", "kafkadefaultuser");
            StepVerifier.create(defaultPublisher.publish(event, null))
                .verifyComplete();
        }
    }

    @Test
    @DisplayName("Should support dynamic topics with RabbitMQ (when available)")
    void shouldSupportDynamicTopicsWithRabbitMQ() {
        // Test EventPublisher with custom destination
        EventPublisher customPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.RABBITMQ, "custom-rabbitmq-exchange");
        
        if (customPublisher != null) {
            assertThat(customPublisher.getDefaultDestination()).isEqualTo("custom-rabbitmq-exchange");
            
            TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create("rabbitmq@example.com", "rabbitmquser");
            
            // Test publishing with custom default
            StepVerifier.create(customPublisher.publish(event, null))
                .verifyComplete();
            
            // Test publishing with explicit destination
            StepVerifier.create(customPublisher.publish(event, "explicit-rabbitmq-exchange"))
                .verifyComplete();
        }
        
        // Test fallback to configured default
        EventPublisher defaultPublisher = publisherFactory.getPublisher(PublisherType.RABBITMQ);
        if (defaultPublisher != null) {
            TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create("rabbitmq-default@example.com", "rabbitmqdefaultuser");
            StepVerifier.create(defaultPublisher.publish(event, null))
                .verifyComplete();
        }
    }

    @Test
    @DisplayName("Should handle null and empty custom destinations gracefully")
    void shouldHandleNullAndEmptyCustomDestinations() {
        // Null custom destination should return standard publisher
        EventPublisher nullDestPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, null);
        EventPublisher standardPublisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        
        assertThat(nullDestPublisher).isEqualTo(standardPublisher);
        
        // Empty custom destination should return standard publisher
        EventPublisher emptyDestPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "");
        
        assertThat(emptyDestPublisher).isEqualTo(standardPublisher);
        
        // Whitespace-only custom destination should return standard publisher
        EventPublisher whitespaceDestPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "   ");
        
        assertThat(whitespaceDestPublisher).isEqualTo(standardPublisher);
    }

    @Test
    @DisplayName("Should support connection-specific dynamic destinations")
    void shouldSupportConnectionSpecificDynamicDestinations() {
        // Test with specific connection ID
        EventPublisher connectionPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "primary", "connection-specific-events");
        
        assertThat(connectionPublisher).isNotNull();
        assertThat(connectionPublisher.getDefaultDestination()).isEqualTo("connection-specific-events");
        
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create("connection@example.com", "connectionuser");

        StepVerifier.create(connectionPublisher.publish(event, null))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should maintain publisher health and availability with custom destinations")
    void shouldMaintainPublisherHealthWithCustomDestinations() {
        EventPublisher customPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "health-test-events");
        
        assertThat(customPublisher).isNotNull();
        assertThat(customPublisher.isAvailable()).isTrue();
        
        // Health should include custom destination information
        StepVerifier.create(customPublisher.getHealth())
            .assertNext(health -> {
                assertThat(health).isNotNull();
                assertThat(health.isAvailable()).isTrue();
                assertThat(health.getDetails()).containsKey("customDefaultDestination");
                assertThat(health.getDetails().get("customDefaultDestination")).isEqualTo("health-test-events");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support all publisher types with dynamic destinations")
    void shouldSupportAllPublisherTypesWithDynamicDestinations() {
        for (PublisherType type : PublisherType.values()) {
            EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                type, "test-" + type.name().toLowerCase() + "-events");

            // Publisher might be null if the type is not configured/available
            if (publisher != null) {
                assertThat(publisher.getDefaultDestination())
                    .isEqualTo("test-" + type.name().toLowerCase() + "-events");

                // AUTO type gets resolved to the actual available publisher type
                if (type == PublisherType.AUTO) {
                    // AUTO should resolve to APPLICATION_EVENT in test environment
                    assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.APPLICATION_EVENT);
                } else {
                    assertThat(publisher.getPublisherType()).isEqualTo(type);
                }
            }
        }
    }

    @Test
    @DisplayName("Should demonstrate multi-service event routing scenario")
    void shouldDemonstrateMultiServiceEventRouting() {
        // Simulate different services with their own event topics
        EventPublisher userServicePublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "user-service-events");
        EventPublisher orderServicePublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "order-service-events");
        EventPublisher auditServicePublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.APPLICATION_EVENT, "audit-service-events");
        
        assertThat(userServicePublisher).isNotNull();
        assertThat(orderServicePublisher).isNotNull();
        assertThat(auditServicePublisher).isNotNull();
        
        TestEventModels.UserRegisteredEvent userEvent = TestEventModels.UserRegisteredEvent.create("multiservice@example.com", "multiserviceuser");
        
        // Each service publishes to its own topic
        StepVerifier.create(userServicePublisher.publish(userEvent, null))
            .verifyComplete();
        
        StepVerifier.create(orderServicePublisher.publish(userEvent, null))
            .verifyComplete();
        
        StepVerifier.create(auditServicePublisher.publish(userEvent, null))
            .verifyComplete();
        
        // Verify each has correct default destination
        assertThat(userServicePublisher.getDefaultDestination()).isEqualTo("user-service-events");
        assertThat(orderServicePublisher.getDefaultDestination()).isEqualTo("order-service-events");
        assertThat(auditServicePublisher.getDefaultDestination()).isEqualTo("audit-service-events");
    }

    @Test
    @DisplayName("Should support @PublishResult with dynamic destinations using SpEL")
    void shouldSupportPublishResultWithDynamicDestinations() {
        // Test tenant-based dynamic destination
        StepVerifier.create(dynamicTopicTestService.createUserWithDynamicTopic("tenant1", "user123"))
            .assertNext(event -> {
                assertThat(event.getTenantId()).isEqualTo("tenant1");
                assertThat(event.getUserId()).isEqualTo("user123");
            })
            .verifyComplete();

        // Test with different tenant
        StepVerifier.create(dynamicTopicTestService.createUserWithDynamicTopic("tenant2", "user456"))
            .assertNext(event -> {
                assertThat(event.getTenantId()).isEqualTo("tenant2");
                assertThat(event.getUserId()).isEqualTo("user456");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support @PublishResult with conditional destinations")
    void shouldSupportPublishResultWithConditionalDestinations() {
        // Test premium user (should go to premium-user-events)
        StepVerifier.create(dynamicTopicTestService.createUserWithTierBasedTopic("premium-user", true))
            .assertNext(event -> {
                assertThat(event.getUserId()).isEqualTo("premium-user");
                assertThat(event.isPremium()).isTrue();
            })
            .verifyComplete();

        // Test standard user (should go to standard-user-events)
        StepVerifier.create(dynamicTopicTestService.createUserWithTierBasedTopic("standard-user", false))
            .assertNext(event -> {
                assertThat(event.getUserId()).isEqualTo("standard-user");
                assertThat(event.isPremium()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support @PublishResult with default destination fallback")
    void shouldSupportPublishResultWithDefaultDestination() {
        // Test without explicit destination (should use configured default)
        StepVerifier.create(dynamicTopicTestService.createUserWithDefaultTopic("default-user"))
            .assertNext(event -> {
                assertThat(event.getUserId()).isEqualTo("default-user");
                assertThat(event.getEmail()).isEqualTo("default-user@example.com");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support programmatic publishing with custom topics")
    void shouldSupportProgrammaticPublishingWithCustomTopics() {
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.builder()
            .userId("test-user")
            .email("test@example.com")
            .build();

        // Test custom topic
        StepVerifier.create(dynamicTopicTestService.publishToCustomTopic("custom-test-topic", event))
            .verifyComplete();

        // Test explicit destination override
        StepVerifier.create(dynamicTopicTestService.publishWithExplicitDestination(
            "custom-default", "explicit-override", event))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support multi-platform publishing with dynamic destinations")
    void shouldSupportMultiPlatformPublishingWithDynamicDestinations() {
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.builder()
            .userId("multi-platform-user")
            .email("multi@example.com")
            .build();

        // This should attempt to publish to Kafka, RabbitMQ, and Spring Events
        // with topic names like "kafka-test-events", "rabbitmq-test-events", "spring-test-events"
        StepVerifier.create(dynamicTopicTestService.publishToAllPlatforms("test-events", event))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should demonstrate destination resolution priority")
    void shouldDemonstrateDestinationResolutionPriority() {
        // Test that custom default destination is set correctly
        String customDefault = dynamicTopicTestService.testDestinationResolutionPriority(
            "priority-test-events", "explicit-override");

        assertThat(customDefault).isEqualTo("priority-test-events");
    }

    @Test
    @DisplayName("Should support connection-specific dynamic destinations")
    void shouldSupportConnectionSpecificDynamicDestinationsInService() {
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.builder()
            .userId("connection-test-user")
            .email("connection@example.com")
            .build();

        StepVerifier.create(dynamicTopicTestService.publishWithSpecificConnection(
            "primary", "connection-specific-events", event))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should support default publisher with custom destination")
    void shouldSupportDefaultPublisherWithCustomDestination() {
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.builder()
            .userId("default-publisher-user")
            .email("default@example.com")
            .build();

        StepVerifier.create(dynamicTopicTestService.publishWithDefaultPublisher(
            "default-custom-events", event))
            .verifyComplete();
    }
}
