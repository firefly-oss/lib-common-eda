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

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventPublisher} annotation.
 * Tests annotation presence, default values, and custom configurations.
 */
@DisplayName("EventPublisher Annotation Tests")
class EventPublisherAnnotationTest {

    @Test
    @DisplayName("Should have correct annotation metadata")
    void shouldHaveCorrectAnnotationMetadata() {
        // Assert
        assertThat(EventPublisher.class.isAnnotation()).isTrue();
        assertThat(EventPublisher.class.getAnnotation(java.lang.annotation.Target.class))
                .isNotNull();
        assertThat(EventPublisher.class.getAnnotation(java.lang.annotation.Retention.class))
                .isNotNull();
        assertThat(EventPublisher.class.getAnnotation(java.lang.annotation.Documented.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Should detect annotation on method with defaults")
    void shouldDetectAnnotationOnMethodWithDefaults() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithDefaults", String.class);

        // Act
        EventPublisher annotation = method.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.publisherType()).isEqualTo(PublisherType.AUTO);
        assertThat(annotation.destination()).isEmpty();
        assertThat(annotation.eventType()).isEmpty();
        assertThat(annotation.connectionId()).isEqualTo("default");
        assertThat(annotation.serializer()).isEmpty();
        assertThat(annotation.async()).isTrue();
        assertThat(annotation.timeoutMs()).isEqualTo(0);
        assertThat(annotation.parameterIndex()).isEqualTo(-1);
        assertThat(annotation.condition()).isEmpty();
        assertThat(annotation.key()).isEmpty();
        assertThat(annotation.headers()).isEmpty();
        assertThat(annotation.timing()).isEqualTo(EventPublisher.PublishTiming.BEFORE);
    }

    @Test
    @DisplayName("Should detect annotation with custom values")
    void shouldDetectAnnotationWithCustomValues() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithCustomValues", String.class, Integer.class);

        // Act
        EventPublisher annotation = method.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.publisherType()).isEqualTo(PublisherType.KAFKA);
        assertThat(annotation.destination()).isEqualTo("user-commands");
        assertThat(annotation.eventType()).isEqualTo("user.create.command");
        assertThat(annotation.connectionId()).isEqualTo("primary");
        assertThat(annotation.serializer()).isEqualTo("customSerializer");
        assertThat(annotation.async()).isFalse();
        assertThat(annotation.timeoutMs()).isEqualTo(5000);
        assertThat(annotation.parameterIndex()).isEqualTo(0);
        assertThat(annotation.condition()).isEqualTo("#param0 != null");
        assertThat(annotation.key()).isEqualTo("#param0.id");
        assertThat(annotation.headers()).containsExactly("source=api", "version=1.0");
        assertThat(annotation.timing()).isEqualTo(EventPublisher.PublishTiming.BEFORE);
    }

    @Test
    @DisplayName("Should support different publish timings")
    void shouldSupportDifferentPublishTimings() throws NoSuchMethodException {
        // Arrange
        Method method1 = TestService.class.getMethod("publishBefore", String.class);
        Method method2 = TestService.class.getMethod("publishAfter", String.class);
        Method method3 = TestService.class.getMethod("publishBoth", String.class);

        // Act
        EventPublisher annotation1 = method1.getAnnotation(EventPublisher.class);
        EventPublisher annotation2 = method2.getAnnotation(EventPublisher.class);
        EventPublisher annotation3 = method3.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation1.timing()).isEqualTo(EventPublisher.PublishTiming.BEFORE);
        assertThat(annotation2.timing()).isEqualTo(EventPublisher.PublishTiming.AFTER);
        assertThat(annotation3.timing()).isEqualTo(EventPublisher.PublishTiming.BOTH);
    }

    @Test
    @DisplayName("Should support specific parameter index")
    void shouldSupportSpecificParameterIndex() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("publishFirstParameter", String.class, Integer.class, Boolean.class);

        // Act
        EventPublisher annotation = method.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.parameterIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should support publishing all parameters")
    void shouldSupportPublishingAllParameters() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("publishAllParameters", String.class, Integer.class);

        // Act
        EventPublisher annotation = method.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.parameterIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should support SpEL expressions")
    void shouldSupportSpelExpressions() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithSpelExpressions", String.class);

        // Act
        EventPublisher annotation = method.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.destination()).isEqualTo("#{#param0.type}-events");
        assertThat(annotation.condition()).isEqualTo("#param0 != null && #param0.isValid()");
        assertThat(annotation.key()).isEqualTo("#param0.userId");
    }

    @Test
    @DisplayName("Should support combination with PublishResult")
    void shouldSupportCombinationWithPublishResult() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithBothAnnotations", String.class);

        // Act
        EventPublisher publisherAnnotation = method.getAnnotation(EventPublisher.class);
        PublishResult resultAnnotation = method.getAnnotation(PublishResult.class);

        // Assert
        assertThat(publisherAnnotation).isNotNull();
        assertThat(resultAnnotation).isNotNull();
        assertThat(publisherAnnotation.destination()).isEqualTo("user-commands");
        assertThat(resultAnnotation.destination()).isEqualTo("user-events");
    }

    @Test
    @DisplayName("Should not be present on unannotated method")
    void shouldNotBePresentOnUnannotatedMethod() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("unannotatedMethod", String.class);

        // Act
        EventPublisher annotation = method.getAnnotation(EventPublisher.class);

        // Assert
        assertThat(annotation).isNull();
    }

    // Test service class with annotated methods
    static class TestService {

        @EventPublisher
        public String methodWithDefaults(String param) {
            return "result";
        }

        @EventPublisher(
                publisherType = PublisherType.KAFKA,
                destination = "user-commands",
                eventType = "user.create.command",
                connectionId = "primary",
                serializer = "customSerializer",
                async = false,
                timeoutMs = 5000,
                parameterIndex = 0,
                condition = "#param0 != null",
                key = "#param0.id",
                headers = {"source=api", "version=1.0"},
                timing = EventPublisher.PublishTiming.BEFORE
        )
        public String methodWithCustomValues(String param1, Integer param2) {
            return "result";
        }

        @EventPublisher(timing = EventPublisher.PublishTiming.BEFORE)
        public String publishBefore(String param) {
            return "result";
        }

        @EventPublisher(timing = EventPublisher.PublishTiming.AFTER)
        public String publishAfter(String param) {
            return "result";
        }

        @EventPublisher(timing = EventPublisher.PublishTiming.BOTH)
        public String publishBoth(String param) {
            return "result";
        }

        @EventPublisher(parameterIndex = 0)
        public String publishFirstParameter(String param1, Integer param2, Boolean param3) {
            return "result";
        }

        @EventPublisher(parameterIndex = -1)
        public String publishAllParameters(String param1, Integer param2) {
            return "result";
        }

        @EventPublisher(
                destination = "#{#param0.type}-events",
                condition = "#param0 != null && #param0.isValid()",
                key = "#param0.userId"
        )
        public String methodWithSpelExpressions(String param) {
            return "result";
        }

        @EventPublisher(destination = "user-commands", eventType = "user.create.requested")
        @PublishResult(destination = "user-events", eventType = "user.created")
        public String methodWithBothAnnotations(String param) {
            return "result";
        }

        public String unannotatedMethod(String param) {
            return "result";
        }
    }
}

