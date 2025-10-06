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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DestinationAwarePublisher Tests")
class DestinationAwarePublisherTest {

    @Mock(lenient = true)
    private EventPublisher delegate;

    private DestinationAwarePublisher publisher;
    private final String customDefaultDestination = "custom-destination";

    @BeforeEach
    void setUp() {
        when(delegate.getPublisherType()).thenReturn(PublisherType.KAFKA);
        when(delegate.isAvailable()).thenReturn(true);
        when(delegate.getDefaultDestination()).thenReturn("delegate-default");
        
        publisher = new DestinationAwarePublisher(delegate, customDefaultDestination);
    }

    @Test
    @DisplayName("Should use custom default destination when no destination provided")
    void shouldUseCustomDefaultDestinationWhenNoneProvided() {
        // Given
        Object event = new Object();
        Map<String, Object> headers = Map.of("key", "value");
        when(delegate.publish(any(), eq(customDefaultDestination), any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisher.publish(event, null, headers);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, customDefaultDestination, headers);
    }

    @Test
    @DisplayName("Should use custom default destination when empty string provided")
    void shouldUseCustomDefaultDestinationWhenEmptyStringProvided() {
        // Given
        Object event = new Object();
        when(delegate.publish(any(), eq(customDefaultDestination), any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisher.publish(event, "", null);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, customDefaultDestination, null);
    }

    @Test
    @DisplayName("Should use custom default destination when whitespace provided")
    void shouldUseCustomDefaultDestinationWhenWhitespaceProvided() {
        // Given
        Object event = new Object();
        when(delegate.publish(any(), eq(customDefaultDestination), any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisher.publish(event, "   ", null);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, customDefaultDestination, null);
    }

    @Test
    @DisplayName("Should use explicit destination when provided")
    void shouldUseExplicitDestinationWhenProvided() {
        // Given
        Object event = new Object();
        String explicitDestination = "explicit-destination";
        when(delegate.publish(any(), eq(explicitDestination), any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisher.publish(event, explicitDestination, null);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, explicitDestination, null);
    }

    @Test
    @DisplayName("Should fall back to delegate default when custom default is null")
    void shouldFallBackToDelegateDefaultWhenCustomDefaultIsNull() {
        // Given
        DestinationAwarePublisher publisherWithNullCustom = new DestinationAwarePublisher(delegate, null);
        Object event = new Object();
        String delegateDefault = "delegate-default";
        when(delegate.publish(any(), eq(delegateDefault), any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisherWithNullCustom.publish(event, null, null);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, delegateDefault, null);
    }

    @Test
    @DisplayName("Should fall back to delegate default when custom default is empty")
    void shouldFallBackToDelegateDefaultWhenCustomDefaultIsEmpty() {
        // Given
        DestinationAwarePublisher publisherWithEmptyCustom = new DestinationAwarePublisher(delegate, "");
        Object event = new Object();
        String delegateDefault = "delegate-default";
        when(delegate.publish(any(), eq(delegateDefault), any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisherWithEmptyCustom.publish(event, null, null);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, delegateDefault, null);
    }

    @Test
    @DisplayName("Should delegate isAvailable to underlying publisher")
    void shouldDelegateIsAvailableToUnderlyingPublisher() {
        // Given
        when(delegate.isAvailable()).thenReturn(false);

        // When
        boolean available = publisher.isAvailable();

        // Then
        assertThat(available).isFalse();
        verify(delegate).isAvailable();
    }

    @Test
    @DisplayName("Should delegate getPublisherType to underlying publisher")
    void shouldDelegateGetPublisherTypeToUnderlyingPublisher() {
        // When
        PublisherType type = publisher.getPublisherType();

        // Then
        assertThat(type).isEqualTo(PublisherType.KAFKA);
        verify(delegate).getPublisherType();
    }

    @Test
    @DisplayName("Should return custom default destination from getDefaultDestination")
    void shouldReturnCustomDefaultDestinationFromGetDefaultDestination() {
        // When
        String defaultDestination = publisher.getDefaultDestination();

        // Then
        assertThat(defaultDestination).isEqualTo(customDefaultDestination);
    }

    @Test
    @DisplayName("Should return delegate default when custom default is null")
    void shouldReturnDelegateDefaultWhenCustomDefaultIsNull() {
        // Given
        DestinationAwarePublisher publisherWithNullCustom = new DestinationAwarePublisher(delegate, null);

        // When
        String defaultDestination = publisherWithNullCustom.getDefaultDestination();

        // Then
        assertThat(defaultDestination).isEqualTo("delegate-default");
        verify(delegate).getDefaultDestination();
    }

    @Test
    @DisplayName("Should enhance health information with custom destination details")
    void shouldEnhanceHealthInformationWithCustomDestinationDetails() {
        // Given
        PublisherHealth delegateHealth = PublisherHealth.builder()
                .publisherType(PublisherType.KAFKA)
                .available(true)
                .status("UP")
                .details(Map.of("original", "health"))
                .build();
        when(delegate.getHealth()).thenReturn(Mono.just(delegateHealth));

        // When
        Mono<PublisherHealth> healthMono = publisher.getHealth();

        // Then
        StepVerifier.create(healthMono)
                .assertNext(health -> {
                    assertThat(health.getPublisherType()).isEqualTo(PublisherType.KAFKA);
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getStatus()).isEqualTo("UP");
                    
                    Map<String, Object> details = health.getDetails();
                    assertThat(details).containsKey("delegateHealth");
                    assertThat(details).containsKey("customDefaultDestination");
                    assertThat(details).containsKey("effectiveDefaultDestination");
                    assertThat(details.get("customDefaultDestination")).isEqualTo(customDefaultDestination);
                    assertThat(details.get("effectiveDefaultDestination")).isEqualTo(customDefaultDestination);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should delegate close to underlying publisher")
    void shouldDelegateCloseToUnderlyingPublisher() {
        // When
        publisher.close();

        // Then
        verify(delegate).close();
    }

    @Test
    @DisplayName("Should provide access to delegate and custom destination")
    void shouldProvideAccessToDelegateAndCustomDestination() {
        // When & Then
        assertThat(publisher.getDelegate()).isSameAs(delegate);
        assertThat(publisher.getCustomDefaultDestination()).isEqualTo(customDefaultDestination);
    }

    @Test
    @DisplayName("Should handle publish with simplified signature")
    void shouldHandlePublishWithSimplifiedSignature() {
        // Given
        Object event = new Object();
        String explicitDestination = "explicit-destination";
        when(delegate.publish(any(), eq(explicitDestination))).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisher.publish(event, explicitDestination);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, explicitDestination);
    }

    @Test
    @DisplayName("Should use custom default with simplified publish when no destination provided")
    void shouldUseCustomDefaultWithSimplifiedPublishWhenNoDestinationProvided() {
        // Given
        Object event = new Object();
        when(delegate.publish(any(), eq(customDefaultDestination))).thenReturn(Mono.empty());

        // When
        Mono<Void> result = publisher.publish(event, null);

        // Then
        StepVerifier.create(result).verifyComplete();
        verify(delegate).publish(event, customDefaultDestination);
    }

    @Test
    @DisplayName("Should handle error propagation from delegate")
    void shouldHandleErrorPropagationFromDelegate() {
        // Given
        Object event = new Object();
        RuntimeException error = new RuntimeException("Publish failed");
        when(delegate.publish(any(), anyString(), any())).thenReturn(Mono.error(error));

        // When
        Mono<Void> result = publisher.publish(event, null, null);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("Should handle destination resolution priority correctly")
    void shouldHandleDestinationResolutionPriorityCorrectly() {
        // Test priority: explicit > custom default > delegate default

        // 1. Explicit destination (highest priority)
        Object event = new Object();
        String explicitDest = "explicit";
        when(delegate.publish(any(), eq(explicitDest), any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publish(event, explicitDest, null))
                .verifyComplete();
        verify(delegate).publish(event, explicitDest, null);

        // 2. Custom default (when no explicit)
        reset(delegate);
        when(delegate.publish(any(), eq(customDefaultDestination), any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publish(event, null, null))
                .verifyComplete();
        verify(delegate).publish(event, customDefaultDestination, null);

        // 3. Delegate default (when custom is null)
        DestinationAwarePublisher publisherWithNullCustom = new DestinationAwarePublisher(delegate, null);
        reset(delegate);
        when(delegate.getDefaultDestination()).thenReturn("delegate-default");
        when(delegate.publish(any(), eq("delegate-default"), any())).thenReturn(Mono.empty());

        StepVerifier.create(publisherWithNullCustom.publish(event, null, null))
                .verifyComplete();
        verify(delegate).publish(event, "delegate-default", null);
    }
}
