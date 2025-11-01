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

import com.firefly.common.eda.annotation.EventPublisher;
import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.integration.BaseIntegrationTest;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for @EventPublisher aspect.
 */
@DisplayName("EventPublisher Aspect Integration Tests")
class EventPublisherAspectIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestEventPublisherService testService;

    @Autowired
    private CommandEventCaptor eventCaptor;

    @Test
    @DisplayName("Should publish parameter BEFORE method execution")
    void shouldPublishParameterBeforeMethodExecution() {
        // Arrange
        eventCaptor.clear();
        CreateUserCommand command = CreateUserCommand.builder()
                .userId("user-123")
                .tenantId("tenant-1")
                .build();

        // Act
        StepVerifier.create(testService.createUserWithBeforeTiming(command))
                .expectNext("user-123")
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedCommands()).hasSize(1);
            Object event = eventCaptor.getCapturedCommands().get(0);
            assertThat(event).isInstanceOf(CreateUserCommand.class);
            CreateUserCommand capturedCommand = (CreateUserCommand) event;
            assertThat(capturedCommand.getUserId()).isEqualTo("user-123");
        });
    }

    @Test
    @DisplayName("Should publish parameter AFTER method execution")
    void shouldPublishParameterAfterMethodExecution() {
        // Arrange
        eventCaptor.clear();
        CreateUserCommand command = CreateUserCommand.builder()
                .userId("user-456")
                .tenantId("tenant-2")
                .build();

        // Act
        StepVerifier.create(testService.createUserWithAfterTiming(command))
                .expectNext("user-456")
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedCommands()).hasSize(1);
        });
    }

    @Test
    @DisplayName("Should publish parameter BOTH before and after")
    void shouldPublishParameterBothBeforeAndAfter() {
        // Arrange
        eventCaptor.clear();
        CreateUserCommand command = CreateUserCommand.builder()
                .userId("user-789")
                .tenantId("tenant-3")
                .build();

        // Act
        StepVerifier.create(testService.createUserWithBothTiming(command))
                .expectNext("user-789")
                .verifyComplete();

        // Assert - should publish twice (before and after)
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedCommands()).hasSize(2);
        });
    }

    @Test
    @DisplayName("Should publish with dynamic SpEL destination")
    void shouldPublishWithDynamicSpelDestination() {
        // Arrange
        eventCaptor.clear();
        CreateUserCommand command = CreateUserCommand.builder()
                .userId("user-999")
                .tenantId("tenant-dynamic")
                .build();

        // Act
        StepVerifier.create(testService.createUserWithDynamicDestination(command))
                .expectNext("user-999")
                .verifyComplete();

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedCommands()).hasSize(1);
            CreateUserCommand capturedCommand = (CreateUserCommand) eventCaptor.getCapturedCommands().get(0);
            assertThat(capturedCommand.getTenantId()).isEqualTo("tenant-dynamic");
        });
    }

    @Test
    @DisplayName("Should respect condition and not publish when false")
    void shouldRespectConditionAndNotPublishWhenFalse() {
        // Arrange
        eventCaptor.clear();

        // Act - null command should not be published
        StepVerifier.create(testService.createUserWithCondition(null))
                .expectNext("default-user")
                .verifyComplete();

        // Assert - no event should be published
        await().during(java.time.Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(eventCaptor.getCapturedCommands()).isEmpty());
    }

    @Test
    @DisplayName("Should publish all parameters when parameterIndex is -1")
    void shouldPublishAllParametersWhenIndexIsMinusOne() {
        // Arrange
        eventCaptor.clear();

        // Act
        testService.auditAction("user-111", "CREATE_USER");

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedMaps()).hasSize(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) eventCaptor.getCapturedMaps().get(0);
            assertThat(params).containsEntry("param0", "user-111");
            assertThat(params).containsEntry("param1", "CREATE_USER");
        });
    }

    @Test
    @DisplayName("Should publish specific parameter by index")
    void shouldPublishSpecificParameterByIndex() {
        // Arrange
        eventCaptor.clear();
        CreateUserCommand command = CreateUserCommand.builder()
                .userId("user-222")
                .tenantId("tenant-4")
                .build();

        // Act
        testService.createUserWithParameterIndex(command, "extra-data");

        // Assert
        await().untilAsserted(() -> {
            assertThat(eventCaptor.getCapturedCommands()).hasSize(1);
            CreateUserCommand capturedCommand = (CreateUserCommand) eventCaptor.getCapturedCommands().get(0);
            assertThat(capturedCommand.getUserId()).isEqualTo("user-222");
        });
    }

    /**
     * Test service with @EventPublisher annotated methods.
     */
    @Service
    static class TestEventPublisherService {

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-commands",
                eventType = "user.create.command",
                timing = EventPublisher.PublishTiming.BEFORE,
                parameterIndex = 0
        )
        public Mono<String> createUserWithBeforeTiming(CreateUserCommand command) {
            return Mono.just(command.getUserId());
        }

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-commands",
                eventType = "user.create.command",
                timing = EventPublisher.PublishTiming.AFTER,
                parameterIndex = 0
        )
        public Mono<String> createUserWithAfterTiming(CreateUserCommand command) {
            return Mono.just(command.getUserId());
        }

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-commands",
                eventType = "user.create.command",
                timing = EventPublisher.PublishTiming.BOTH,
                parameterIndex = 0
        )
        public Mono<String> createUserWithBothTiming(CreateUserCommand command) {
            return Mono.just(command.getUserId());
        }

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "#{#param0.tenantId}-commands",
                eventType = "user.create.command",
                timing = EventPublisher.PublishTiming.BEFORE,
                parameterIndex = 0
        )
        public Mono<String> createUserWithDynamicDestination(CreateUserCommand command) {
            return Mono.just(command.getUserId());
        }

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-commands",
                eventType = "user.create.command",
                condition = "#param0 != null",
                parameterIndex = 0
        )
        public Mono<String> createUserWithCondition(CreateUserCommand command) {
            return Mono.just(command != null ? command.getUserId() : "default-user");
        }

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "audit-events",
                eventType = "audit.action",
                parameterIndex = -1
        )
        public void auditAction(String userId, String action) {
            // Method implementation
        }

        @EventPublisher(
                publisherType = PublisherType.APPLICATION_EVENT,
                destination = "user-commands",
                eventType = "user.create.command",
                parameterIndex = 0
        )
        public void createUserWithParameterIndex(CreateUserCommand command, String extraData) {
            // Method implementation
        }
    }

    /**
     * Event captor to capture published commands.
     */
    @Component
    static class CommandEventCaptor {
        private final List<Object> capturedCommands = new CopyOnWriteArrayList<>();
        private final List<Object> capturedMaps = new CopyOnWriteArrayList<>();

        @EventListener
        public void captureCommand(CreateUserCommand command) {
            capturedCommands.add(command);
        }

        @EventListener
        public void captureMap(Map<?, ?> map) {
            if (map.containsKey("param0") || map.containsKey("param1")) {
                capturedMaps.add(map);
            }
        }

        public List<Object> getCapturedCommands() {
            return new ArrayList<>(capturedCommands);
        }

        public List<Object> getCapturedMaps() {
            return new ArrayList<>(capturedMaps);
        }

        public void clear() {
            capturedCommands.clear();
            capturedMaps.clear();
        }
    }

    /**
     * Test command model.
     */
    @Data
    @Builder
    static class CreateUserCommand {
        private String userId;
        private String tenantId;
    }
}

