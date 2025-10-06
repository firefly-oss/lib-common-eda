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
import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.resilience.ResilientEventPublisherFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisherFactory Dynamic Destination Tests")
class EventPublisherFactoryDynamicDestinationTest {

    @Mock(lenient = true)
    private EventPublisher mockKafkaPublisher;

    @Mock(lenient = true)
    private EventPublisher mockRabbitMqPublisher;

    @Mock(lenient = true)
    private EventPublisher mockApplicationEventPublisher;

    @Mock(lenient = true)
    private EdaProperties edaProperties;

    @Mock(lenient = true)
    private ObjectProvider<ResilientEventPublisherFactory> resilienceFactoryProvider;

    private EventPublisherFactory factory;

    @BeforeEach
    void setUp() {
        // Setup mock publishers
        when(mockKafkaPublisher.getPublisherType()).thenReturn(PublisherType.KAFKA);
        when(mockKafkaPublisher.isAvailable()).thenReturn(true);
        when(mockKafkaPublisher.getDefaultDestination()).thenReturn("default-kafka-topic");

        when(mockRabbitMqPublisher.getPublisherType()).thenReturn(PublisherType.RABBITMQ);
        when(mockRabbitMqPublisher.isAvailable()).thenReturn(true);
        when(mockRabbitMqPublisher.getDefaultDestination()).thenReturn("default-rabbitmq-exchange");

        when(mockApplicationEventPublisher.getPublisherType()).thenReturn(PublisherType.APPLICATION_EVENT);
        when(mockApplicationEventPublisher.isAvailable()).thenReturn(true);
        when(mockApplicationEventPublisher.getDefaultDestination()).thenReturn("default-app-events");

        // Setup EdaProperties
        when(edaProperties.getDefaultConnectionId()).thenReturn("default");
        when(edaProperties.getDefaultPublisherType()).thenReturn(PublisherType.KAFKA);

        // Setup resilience factory
        when(resilienceFactoryProvider.getIfAvailable()).thenReturn(null);

        List<EventPublisher> availablePublishers = List.of(
                mockKafkaPublisher, mockRabbitMqPublisher, mockApplicationEventPublisher
        );

        factory = new EventPublisherFactory(availablePublishers, edaProperties, resilienceFactoryProvider);
    }

    @Test
    @DisplayName("Should return DestinationAwarePublisher when custom destination is provided")
    void shouldReturnDestinationAwarePublisherWithCustomDestination() {
        // When
        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, "custom-topic");

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher.getDefaultDestination()).isEqualTo("custom-topic");
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.KAFKA);
    }

    @Test
    @DisplayName("Should return DestinationAwarePublisher with connection ID and custom destination")
    void shouldReturnDestinationAwarePublisherWithConnectionIdAndCustomDestination() {
        // When
        EventPublisher publisher = factory.getPublisherWithDestination(
                PublisherType.RABBITMQ, "primary", "custom-exchange");

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher.getDefaultDestination()).isEqualTo("custom-exchange");
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.RABBITMQ);
    }

    @Test
    @DisplayName("Should return standard publisher when custom destination is null")
    void shouldReturnStandardPublisherWhenCustomDestinationIsNull() {
        // When
        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, null);

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isNotInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher).isSameAs(mockKafkaPublisher);
    }

    @Test
    @DisplayName("Should return standard publisher when custom destination is empty")
    void shouldReturnStandardPublisherWhenCustomDestinationIsEmpty() {
        // When
        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, "");

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isNotInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher).isSameAs(mockKafkaPublisher);
    }

    @Test
    @DisplayName("Should return standard publisher when custom destination is whitespace")
    void shouldReturnStandardPublisherWhenCustomDestinationIsWhitespace() {
        // When
        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, "   ");

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isNotInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher).isSameAs(mockKafkaPublisher);
    }

    @Test
    @DisplayName("Should return null when base publisher is not available")
    void shouldReturnNullWhenBasePublisherNotAvailable() {
        // When
        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.NOOP, "custom-destination");

        // Then
        assertThat(publisher).isNull();
    }

    @Test
    @DisplayName("Should return default publisher with custom destination")
    void shouldReturnDefaultPublisherWithCustomDestination() {
        // When
        EventPublisher publisher = factory.getDefaultPublisherWithDestination("custom-default-topic");

        // Then
        assertThat(publisher).isNotNull();
        assertThat(publisher).isInstanceOf(DestinationAwarePublisher.class);
        assertThat(publisher.getDefaultDestination()).isEqualTo("custom-default-topic");
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.KAFKA); // Default type
    }

    @Test
    @DisplayName("Should delegate publish calls to underlying publisher with custom destination")
    void shouldDelegatePublishCallsWithCustomDestination() {
        // Given
        String customDestination = "custom-topic";
        Object event = new Object();
        Map<String, Object> headers = Map.of("key", "value");
        
        when(mockKafkaPublisher.publish(any(), anyString(), any())).thenReturn(Mono.empty());

        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, customDestination);

        // When
        Mono<Void> result = publisher.publish(event, null, headers);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(mockKafkaPublisher).publish(event, customDestination, headers);
    }

    @Test
    @DisplayName("Should use explicit destination when provided to DestinationAwarePublisher")
    void shouldUseExplicitDestinationWhenProvided() {
        // Given
        String customDefaultDestination = "custom-default-topic";
        String explicitDestination = "explicit-topic";
        Object event = new Object();
        
        when(mockKafkaPublisher.publish(any(), anyString(), any())).thenReturn(Mono.empty());

        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, customDefaultDestination);

        // When
        Mono<Void> result = publisher.publish(event, explicitDestination, null);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(mockKafkaPublisher).publish(event, explicitDestination, null);
    }

    @Test
    @DisplayName("Should return null when base publisher is not available")
    void shouldReturnNullWhenBasePublisherIsNotAvailable() {
        // Given
        when(mockKafkaPublisher.isAvailable()).thenReturn(false);

        // When
        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, "custom-topic");

        // Then
        assertThat(publisher).isNull();
    }

    @Test
    @DisplayName("Should preserve publisher health through wrapper")
    void shouldPreservePublisherHealthThroughWrapper() {
        // Given
        PublisherHealth originalHealth = PublisherHealth.builder()
                .publisherType(PublisherType.KAFKA)
                .available(true)
                .status("UP")
                .details(Map.of("original", "health"))
                .build();
        
        when(mockKafkaPublisher.getHealth()).thenReturn(Mono.just(originalHealth));

        EventPublisher publisher = factory.getPublisherWithDestination(PublisherType.KAFKA, "custom-topic");

        // When
        Mono<PublisherHealth> healthMono = publisher.getHealth();

        // Then
        StepVerifier.create(healthMono)
                .assertNext(health -> {
                    assertThat(health.getPublisherType()).isEqualTo(PublisherType.KAFKA);
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getStatus()).isEqualTo("UP");
                    assertThat(health.getDetails()).containsKey("delegateHealth");
                    assertThat(health.getDetails()).containsKey("customDefaultDestination");
                    assertThat(health.getDetails()).containsKey("effectiveDefaultDestination");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle all publisher types with custom destinations")
    void shouldHandleAllPublisherTypesWithCustomDestinations() {
        // Test Kafka
        EventPublisher kafkaPublisher = factory.getPublisherWithDestination(
                PublisherType.KAFKA, "custom-kafka-topic");
        assertThat(kafkaPublisher).isNotNull();
        assertThat(kafkaPublisher.getDefaultDestination()).isEqualTo("custom-kafka-topic");

        // Test RabbitMQ
        EventPublisher rabbitPublisher = factory.getPublisherWithDestination(
                PublisherType.RABBITMQ, "custom-rabbitmq-exchange");
        assertThat(rabbitPublisher).isNotNull();
        assertThat(rabbitPublisher.getDefaultDestination()).isEqualTo("custom-rabbitmq-exchange");

        // Test Application Events
        EventPublisher appEventPublisher = factory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, "custom-app-events");
        assertThat(appEventPublisher).isNotNull();
        assertThat(appEventPublisher.getDefaultDestination()).isEqualTo("custom-app-events");
    }
}
