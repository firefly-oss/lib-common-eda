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

package com.firefly.common.eda.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventListener} annotation.
 * Tests annotation presence, default values, and custom configurations.
 */
@DisplayName("EventListener Annotation Tests")
class EventListenerAnnotationTest {

    @Test
    @DisplayName("Should have correct annotation metadata")
    void shouldHaveCorrectAnnotationMetadata() {
        // Assert
        assertThat(EventListener.class.isAnnotation()).isTrue();
        assertThat(EventListener.class.getAnnotation(java.lang.annotation.Target.class))
                .isNotNull();
        assertThat(EventListener.class.getAnnotation(java.lang.annotation.Retention.class))
                .isNotNull();
        assertThat(EventListener.class.getAnnotation(java.lang.annotation.Documented.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Should detect annotation on method with defaults")
    void shouldDetectAnnotationOnMethodWithDefaults() throws NoSuchMethodException {
        // Arrange
        Method method = TestListener.class.getMethod("listenerWithDefaults", Object.class);

        // Act
        EventListener annotation = method.getAnnotation(EventListener.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.destinations()).isEmpty();
        assertThat(annotation.eventTypes()).isEmpty();
        assertThat(annotation.consumerType()).isEqualTo(PublisherType.AUTO);
        assertThat(annotation.connectionId()).isEqualTo("default");
        assertThat(annotation.errorStrategy()).isEqualTo(ErrorHandlingStrategy.LOG_AND_CONTINUE);
        assertThat(annotation.maxRetries()).isEqualTo(3);
        assertThat(annotation.retryDelayMs()).isEqualTo(1000);
        assertThat(annotation.autoAck()).isTrue();
        assertThat(annotation.groupId()).isEmpty();
        assertThat(annotation.condition()).isEmpty();
        assertThat(annotation.priority()).isEqualTo(0);
        assertThat(annotation.async()).isTrue();
        assertThat(annotation.timeoutMs()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should detect annotation with custom values")
    void shouldDetectAnnotationWithCustomValues() throws NoSuchMethodException {
        // Arrange
        Method method = TestListener.class.getMethod("listenerWithCustomValues", Object.class);

        // Act
        EventListener annotation = method.getAnnotation(EventListener.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.destinations()).containsExactly("user-events", "order-events");
        assertThat(annotation.eventTypes()).containsExactly("user.created", "user.updated");
        assertThat(annotation.consumerType()).isEqualTo(PublisherType.KAFKA);
        assertThat(annotation.connectionId()).isEqualTo("primary");
        assertThat(annotation.errorStrategy()).isEqualTo(ErrorHandlingStrategy.DEAD_LETTER);
        assertThat(annotation.maxRetries()).isEqualTo(5);
        assertThat(annotation.retryDelayMs()).isEqualTo(2000);
        assertThat(annotation.autoAck()).isFalse();
        assertThat(annotation.groupId()).isEqualTo("user-service");
        assertThat(annotation.condition()).isEqualTo("#event.headers['priority'] == 'high'");
        assertThat(annotation.priority()).isEqualTo(10);
        assertThat(annotation.async()).isFalse();
        assertThat(annotation.timeoutMs()).isEqualTo(30000);
    }

    @Test
    @DisplayName("Should support wildcard event types")
    void shouldSupportWildcardEventTypes() throws NoSuchMethodException {
        // Arrange
        Method method = TestListener.class.getMethod("listenerWithWildcards", Object.class);

        // Act
        EventListener annotation = method.getAnnotation(EventListener.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.eventTypes()).containsExactly("user.*", "order.*");
    }

    @Test
    @DisplayName("Should support multiple destinations")
    void shouldSupportMultipleDestinations() throws NoSuchMethodException {
        // Arrange
        Method method = TestListener.class.getMethod("listenerWithMultipleDestinations", Object.class);

        // Act
        EventListener annotation = method.getAnnotation(EventListener.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.destinations()).hasSize(3);
        assertThat(annotation.destinations()).containsExactly(
                "topic1",
                "topic2",
                "topic3"
        );
    }

    @Test
    @DisplayName("Should support different error handling strategies")
    void shouldSupportDifferentErrorHandlingStrategies() throws NoSuchMethodException {
        // Arrange
        Method method1 = TestListener.class.getMethod("listenerWithLogAndContinue", Object.class);
        Method method2 = TestListener.class.getMethod("listenerWithDeadLetterQueue", Object.class);
        Method method3 = TestListener.class.getMethod("listenerWithRetryAndFail", Object.class);

        // Act
        EventListener annotation1 = method1.getAnnotation(EventListener.class);
        EventListener annotation2 = method2.getAnnotation(EventListener.class);
        EventListener annotation3 = method3.getAnnotation(EventListener.class);

        // Assert
        assertThat(annotation1.errorStrategy()).isEqualTo(ErrorHandlingStrategy.LOG_AND_CONTINUE);
        assertThat(annotation2.errorStrategy()).isEqualTo(ErrorHandlingStrategy.DEAD_LETTER);
        assertThat(annotation3.errorStrategy()).isEqualTo(ErrorHandlingStrategy.LOG_AND_RETRY);
    }

    @Test
    @DisplayName("Should not be present on unannotated method")
    void shouldNotBePresentOnUnannotatedMethod() throws NoSuchMethodException {
        // Arrange
        Method method = TestListener.class.getMethod("unannotatedMethod", Object.class);

        // Act
        EventListener annotation = method.getAnnotation(EventListener.class);

        // Assert
        assertThat(annotation).isNull();
    }

    // Test listener class with annotated methods
    static class TestListener {

        @EventListener
        public Mono<Void> listenerWithDefaults(Object event) {
            return Mono.empty();
        }

        @EventListener(
                destinations = {"user-events", "order-events"},
                eventTypes = {"user.created", "user.updated"},
                consumerType = PublisherType.KAFKA,
                connectionId = "primary",
                errorStrategy = ErrorHandlingStrategy.DEAD_LETTER,
                maxRetries = 5,
                retryDelayMs = 2000,
                autoAck = false,
                groupId = "user-service",
                condition = "#event.headers['priority'] == 'high'",
                priority = 10,
                async = false,
                timeoutMs = 30000
        )
        public Mono<Void> listenerWithCustomValues(Object event) {
            return Mono.empty();
        }

        @EventListener(eventTypes = {"user.*", "order.*"})
        public Mono<Void> listenerWithWildcards(Object event) {
            return Mono.empty();
        }

        @EventListener(destinations = {"topic1", "topic2", "topic3"})
        public Mono<Void> listenerWithMultipleDestinations(Object event) {
            return Mono.empty();
        }

        @EventListener(errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE)
        public Mono<Void> listenerWithLogAndContinue(Object event) {
            return Mono.empty();
        }

        @EventListener(errorStrategy = ErrorHandlingStrategy.DEAD_LETTER)
        public Mono<Void> listenerWithDeadLetterQueue(Object event) {
            return Mono.empty();
        }

        @EventListener(errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY)
        public Mono<Void> listenerWithRetryAndFail(Object event) {
            return Mono.empty();
        }

        public Mono<Void> unannotatedMethod(Object event) {
            return Mono.empty();
        }
    }
}

