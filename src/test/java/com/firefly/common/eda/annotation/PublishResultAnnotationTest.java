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
 * Unit tests for {@link PublishResult} annotation.
 * Tests annotation presence, default values, and custom configurations.
 */
@DisplayName("PublishResult Annotation Tests")
class PublishResultAnnotationTest {

    @Test
    @DisplayName("Should have correct annotation metadata")
    void shouldHaveCorrectAnnotationMetadata() {
        // Assert
        assertThat(PublishResult.class.isAnnotation()).isTrue();
        assertThat(PublishResult.class.getAnnotation(java.lang.annotation.Target.class))
                .isNotNull();
        assertThat(PublishResult.class.getAnnotation(java.lang.annotation.Retention.class))
                .isNotNull();
        assertThat(PublishResult.class.getAnnotation(java.lang.annotation.Documented.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Should detect annotation on method with defaults")
    void shouldDetectAnnotationOnMethodWithDefaults() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithDefaults");

        // Act
        PublishResult annotation = method.getAnnotation(PublishResult.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.publisherType()).isEqualTo(PublisherType.AUTO);
        assertThat(annotation.destination()).isEmpty();
        assertThat(annotation.eventType()).isEmpty();
        assertThat(annotation.connectionId()).isEqualTo("default");
        assertThat(annotation.serializer()).isEmpty();
        assertThat(annotation.async()).isTrue();
        assertThat(annotation.timeoutMs()).isEqualTo(0);
        assertThat(annotation.publishOnError()).isFalse();
        assertThat(annotation.condition()).isEmpty();
        assertThat(annotation.key()).isEmpty();
        assertThat(annotation.headers()).isEmpty();
    }

    @Test
    @DisplayName("Should detect annotation with custom values")
    void shouldDetectAnnotationWithCustomValues() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithCustomValues");

        // Act
        PublishResult annotation = method.getAnnotation(PublishResult.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.publisherType()).isEqualTo(PublisherType.KAFKA);
        assertThat(annotation.destination()).isEqualTo("user-events");
        assertThat(annotation.eventType()).isEqualTo("user.created");
        assertThat(annotation.connectionId()).isEqualTo("primary");
        assertThat(annotation.serializer()).isEqualTo("customSerializer");
        assertThat(annotation.async()).isFalse();
        assertThat(annotation.timeoutMs()).isEqualTo(5000);
        assertThat(annotation.publishOnError()).isTrue();
        assertThat(annotation.condition()).isEqualTo("#result != null");
        assertThat(annotation.key()).isEqualTo("#result.id");
        assertThat(annotation.headers()).containsExactly("source=api", "version=1.0");
    }

    @Test
    @DisplayName("Should support SpEL expressions in destination")
    void shouldSupportSpelExpressionsInDestination() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithSpelDestination");

        // Act
        PublishResult annotation = method.getAnnotation(PublishResult.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.destination()).isEqualTo("#{#result.type}-events");
    }

    @Test
    @DisplayName("Should support multiple headers")
    void shouldSupportMultipleHeaders() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("methodWithMultipleHeaders");

        // Act
        PublishResult annotation = method.getAnnotation(PublishResult.class);

        // Assert
        assertThat(annotation).isNotNull();
        assertThat(annotation.headers()).hasSize(3);
        assertThat(annotation.headers()).containsExactly(
                "source=api",
                "version=1.0",
                "userId=#{#result.userId}"
        );
    }

    @Test
    @DisplayName("Should not be present on unannotated method")
    void shouldNotBePresentOnUnannotatedMethod() throws NoSuchMethodException {
        // Arrange
        Method method = TestService.class.getMethod("unannotatedMethod");

        // Act
        PublishResult annotation = method.getAnnotation(PublishResult.class);

        // Assert
        assertThat(annotation).isNull();
    }

    // Test service class with annotated methods
    static class TestService {

        @PublishResult
        public String methodWithDefaults() {
            return "result";
        }

        @PublishResult(
                publisherType = PublisherType.KAFKA,
                destination = "user-events",
                eventType = "user.created",
                connectionId = "primary",
                serializer = "customSerializer",
                async = false,
                timeoutMs = 5000,
                publishOnError = true,
                condition = "#result != null",
                key = "#result.id",
                headers = {"source=api", "version=1.0"}
        )
        public String methodWithCustomValues() {
            return "result";
        }

        @PublishResult(destination = "#{#result.type}-events")
        public String methodWithSpelDestination() {
            return "result";
        }

        @PublishResult(headers = {"source=api", "version=1.0", "userId=#{#result.userId}"})
        public String methodWithMultipleHeaders() {
            return "result";
        }

        public String unannotatedMethod() {
            return "result";
        }
    }
}

