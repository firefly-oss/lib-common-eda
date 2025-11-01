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

package com.firefly.common.eda.aspect;

import com.firefly.common.eda.annotation.PublishResult;
import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.integration.BaseIntegrationTest;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for @PublishResult aspect.
 */
@DisplayName("PublishResult Aspect Integration Tests")
class PublishResultAspectIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestPublishResultService testService;

    @Autowired
    private TestEventCaptor eventCaptor;

    @Test
    @DisplayName("Should publish result with static destination")
    void shouldPublishResultWithStaticDestination() {
        // Arrange
        eventCaptor.clear();

        // Act
        StepVerifier.create(testService.createUserWithStaticDestination("user-123"))
                .expectNextMatches(user -> user.getUserId().equals("user-123"))
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedEvents()).hasSize(1);
            Object event = eventCaptor.getCapturedEvents().get(0);
            assertThat(event).isInstanceOf(UserCreatedEvent.class);
            UserCreatedEvent userEvent = (UserCreatedEvent) event;
            assertThat(userEvent.getUserId()).isEqualTo("user-123");
        });
    }

    @Test
    @DisplayName("Should publish result with dynamic SpEL destination")
    void shouldPublishResultWithDynamicSpelDestination() {
        // Arrange
        eventCaptor.clear();

        // Act
        StepVerifier.create(testService.createUserWithDynamicDestination("tenant-1", "user-456"))
                .expectNextMatches(user -> user.getTenantId().equals("tenant-1"))
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedEvents()).hasSize(1);
            Object event = eventCaptor.getCapturedEvents().get(0);
            assertThat(event).isInstanceOf(UserCreatedEvent.class);
            UserCreatedEvent userEvent = (UserCreatedEvent) event;
            assertThat(userEvent.getTenantId()).isEqualTo("tenant-1");
            assertThat(userEvent.getUserId()).isEqualTo("user-456");
        });
    }

    @Test
    @DisplayName("Should respect condition and not publish when false")
    void shouldRespectConditionAndNotPublishWhenFalse() {
        // Arrange
        eventCaptor.clear();

        // Act - inactive user should not be published
        StepVerifier.create(testService.createUserWithCondition("user-789", false))
                .expectNextMatches(user -> !user.isActive())
                .verifyComplete();

        // Assert - no event should be published
        await().during(java.time.Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(eventCaptor.getCapturedEvents()).isEmpty());
    }

    @Test
    @DisplayName("Should publish when condition is true")
    void shouldPublishWhenConditionIsTrue() {
        // Arrange
        eventCaptor.clear();

        // Act - active user should be published
        StepVerifier.create(testService.createUserWithCondition("user-999", true))
                .expectNextMatches(UserCreatedEvent::isActive)
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedEvents()).hasSize(1);
            Object event = eventCaptor.getCapturedEvents().get(0);
            assertThat(event).isInstanceOf(UserCreatedEvent.class);
            UserCreatedEvent userEvent = (UserCreatedEvent) event;
            assertThat(userEvent.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("Should evaluate SpEL in headers")
    void shouldEvaluateSpelInHeaders() {
        // Arrange
        eventCaptor.clear();

        // Act
        StepVerifier.create(testService.createUserWithCustomHeaders("user-111"))
                .expectNextMatches(user -> user.getUserId().equals("user-111"))
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedEvents()).hasSize(1);
        });
    }

    @Test
    @DisplayName("Should handle non-reactive return type")
    void shouldHandleNonReactiveReturnType() {
        // Arrange
        eventCaptor.clear();

        // Act
        UserCreatedEvent result = testService.createUserNonReactive("user-222");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-222");
        
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedEvents()).hasSize(1);
        });
    }

    /**
     * Test service with @PublishResult annotated methods.
     */
    @Service
    static class TestPublishResultService {

        @PublishResult(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-events",
                eventType = "user.created"
        )
        public Mono<UserCreatedEvent> createUserWithStaticDestination(String userId) {
            return Mono.just(UserCreatedEvent.builder()
                    .userId(userId)
                    .tenantId("default")
                    .active(true)
                    .build());
        }

        @PublishResult(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "#{#result.tenantId}-user-events",
                eventType = "user.created"
        )
        public Mono<UserCreatedEvent> createUserWithDynamicDestination(String tenantId, String userId) {
            return Mono.just(UserCreatedEvent.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .active(true)
                    .build());
        }

        @PublishResult(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-events",
                eventType = "user.created",
                condition = "#result != null && #result.active"
        )
        public Mono<UserCreatedEvent> createUserWithCondition(String userId, boolean active) {
            return Mono.just(UserCreatedEvent.builder()
                    .userId(userId)
                    .tenantId("default")
                    .active(active)
                    .build());
        }

        @PublishResult(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-events",
                eventType = "user.created",
                key = "#{#result.userId}",
                headers = {"source=test-service", "priority=high"}
        )
        public Mono<UserCreatedEvent> createUserWithCustomHeaders(String userId) {
            return Mono.just(UserCreatedEvent.builder()
                    .userId(userId)
                    .tenantId("default")
                    .active(true)
                    .build());
        }

        @PublishResult(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-events",
                eventType = "user.created"
        )
        public UserCreatedEvent createUserNonReactive(String userId) {
            return UserCreatedEvent.builder()
                    .userId(userId)
                    .tenantId("default")
                    .active(true)
                    .build();
        }
    }

    /**
     * Event captor to capture published events.
     */
    @Component
    static class TestEventCaptor {
        private final List<Object> capturedEvents = new CopyOnWriteArrayList<>();

        @EventListener
        public void captureEvent(UserCreatedEvent event) {
            capturedEvents.add(event);
        }

        public List<Object> getCapturedEvents() {
            return new ArrayList<>(capturedEvents);
        }

        public void clear() {
            capturedEvents.clear();
        }
    }

    /**
     * Test event model.
     */
    @Data
    @Builder
    static class UserCreatedEvent {
        private String userId;
        private String tenantId;
        private boolean active;
    }
}

