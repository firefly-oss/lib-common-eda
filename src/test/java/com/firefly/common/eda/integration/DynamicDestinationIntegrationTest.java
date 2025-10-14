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
import com.firefly.common.eda.publisher.DestinationAwarePublisher;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "firefly.eda.enabled=true",
        "firefly.eda.default-publisher-type=APPLICATION_EVENT",
        "firefly.eda.publishers.application-event.enabled=true",
        "firefly.eda.publishers.application-event.default-destination=default-app-events"
})
@DisplayName("Dynamic Destination Integration Tests")
class DynamicDestinationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventPublisherFactory publisherFactory;

    @Test
    @DisplayName("Should create publisher with custom destination that overrides configuration")
    void shouldCreatePublisherWithCustomDestinationOverridingConfiguration() {
        // Given
        String customDestination = "custom-events-topic";

        // When
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, customDestination);

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher.getDefaultDestination()).isEqualTo(customDestination);
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.APPLICATION_EVENT);
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should publish events using custom default destination")
    void shouldPublishEventsUsingCustomDefaultDestination() {
        // Given
        String customDestination = "user-events";
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, customDestination);
        
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create(
                "john.doe@example.com", "john.doe");

        // When & Then
        StepVerifier.create(publisher.publish(event, null))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should use explicit destination when provided, overriding custom default")
    void shouldUseExplicitDestinationOverridingCustomDefault() {
        // Given
        String customDefaultDestination = "custom-default";
        String explicitDestination = "explicit-destination";
        
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, customDefaultDestination);
        
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create(
                "john.doe@example.com", "john.doe");

        // When & Then
        StepVerifier.create(publisher.publish(event, explicitDestination))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get default publisher with custom destination")
    void shouldGetDefaultPublisherWithCustomDestination() {
        // Given
        String customDestination = "custom-default-events";

        // When
        EventPublisher publisher = publisherFactory.getDefaultPublisherWithDestination(customDestination);

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher.getDefaultDestination()).isEqualTo(customDestination);
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.APPLICATION_EVENT); // Default type
    }

    @Test
    @DisplayName("Should handle multiple publishers with different custom destinations")
    void shouldHandleMultiplePublishersWithDifferentCustomDestinations() {
        // Given
        String destination1 = "events-topic-1";
        String destination2 = "events-topic-2";

        // When
        EventPublisher publisher1 = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, destination1);
        EventPublisher publisher2 = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, destination2);

        // Then
        assertThat(publisher1).isNotNull();
        assertThat(publisher2).isNotNull();
        assertThat(publisher1.getDefaultDestination()).isEqualTo(destination1);
        assertThat(publisher2.getDefaultDestination()).isEqualTo(destination2);
        assertThat(publisher1).isNotSameAs(publisher2);
    }

    @Test
    @DisplayName("Should provide enhanced health information for destination-aware publisher")
    void shouldProvideEnhancedHealthInformationForDestinationAwarePublisher() {
        // Given
        String customDestination = "health-test-events";
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, customDestination);

        // When & Then
        StepVerifier.create(publisher.getHealth())
                .assertNext(health -> {
                    assertThat(health.getPublisherType()).isEqualTo(PublisherType.APPLICATION_EVENT);
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getStatus()).isEqualTo("UP");
                    
                    Map<String, Object> details = health.getDetails();
                    assertThat(details).containsKey("delegateHealth");
                    assertThat(details).containsKey("customDefaultDestination");
                    assertThat(details).containsKey("effectiveDefaultDestination");
                    assertThat(details.get("customDefaultDestination")).isEqualTo(customDestination);
                    assertThat(details.get("effectiveDefaultDestination")).isEqualTo(customDestination);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should maintain backward compatibility with standard publisher methods")
    void shouldMaintainBackwardCompatibilityWithStandardPublisherMethods() {
        // Given - Get standard publisher (no custom destination)
        EventPublisher standardPublisher = publisherFactory.getPublisher(PublisherType.APPLICATION_EVENT);
        
        // When & Then - Should work as before
        assertThat(standardPublisher).isNotNull();
        assertThat(standardPublisher).isNotInstanceOf(DestinationAwarePublisher.class);
        assertThat(standardPublisher.getDefaultDestination()).isEqualTo("default-app-events"); // From config
        
        TestEventModels.UserRegisteredEvent event = TestEventModels.UserRegisteredEvent.create(
                "john.doe@example.com", "john.doe");
        
        StepVerifier.create(standardPublisher.publish(event, "explicit-destination"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle connection-specific publishers with custom destinations")
    void shouldHandleConnectionSpecificPublishersWithCustomDestinations() {
        // Given
        String connectionId = "primary";
        String customDestination = "connection-specific-events";

        // When
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, connectionId, customDestination);

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher.getDefaultDestination()).isEqualTo(customDestination);
    }

    @Test
    @DisplayName("Should return null when requesting custom destination for unavailable publisher type")
    void shouldReturnNullWhenRequestingCustomDestinationForUnavailablePublisherType() {
        // When - Use RABBITMQ which is not available in test environment (KAFKA may be available)
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.RABBITMQ, "custom-rabbitmq-topic"); // RabbitMQ not available in this test

        // Then
        assertThat(publisher).isNull();
    }

    @Test
    @DisplayName("Should demonstrate real-world usage scenario")
    void shouldDemonstrateRealWorldUsageScenario() {
        // Scenario: Different services need to publish to different topics
        
        // User service publishes to user-events
        EventPublisher userServicePublisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "user-events");
        
        // Order service publishes to order-events  
        EventPublisher orderServicePublisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "order-events");
        
        // Audit service publishes to audit-events
        EventPublisher auditServicePublisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "audit-events");

        // Verify each has correct default destination
        assertThat(userServicePublisher.getDefaultDestination()).isEqualTo("user-events");
        assertThat(orderServicePublisher.getDefaultDestination()).isEqualTo("order-events");
        assertThat(auditServicePublisher.getDefaultDestination()).isEqualTo("audit-events");

        // Verify they can publish events
        TestEventModels.UserRegisteredEvent userEvent = TestEventModels.UserRegisteredEvent.create(
                "john.doe@example.com", "john.doe");
        
        StepVerifier.create(userServicePublisher.publish(userEvent, null))
                .verifyComplete();
        
        StepVerifier.create(orderServicePublisher.publish(userEvent, null))
                .verifyComplete();
        
        StepVerifier.create(auditServicePublisher.publish(userEvent, null))
                .verifyComplete();
    }
}
