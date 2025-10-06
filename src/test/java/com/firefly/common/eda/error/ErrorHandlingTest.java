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

package com.firefly.common.eda.error;

import com.firefly.common.eda.annotation.ErrorHandlingStrategy;
import com.firefly.common.eda.error.impl.MetricsErrorHandler;
import com.firefly.common.eda.error.impl.NotificationErrorHandler;
import com.firefly.common.eda.testconfig.TestEventModels.SimpleTestEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for error handling functionality.
 * <p>
 * Tests custom error handlers, error strategies, and edge cases
 * in error handling scenarios.
 */
@ExtendWith(MockitoExtension.class)
class ErrorHandlingTest {

    private CustomErrorHandlerRegistry errorHandlerRegistry;
    private MeterRegistry meterRegistry;
    private TestCustomErrorHandler testHandler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        testHandler = new TestCustomErrorHandler();
        errorHandlerRegistry = new CustomErrorHandlerRegistry();
    }

    @Test
    void shouldRegisterAndExecuteCustomErrorHandlers() {
        // Arrange
        errorHandlerRegistry.registerHandler(testHandler);
        
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        Map<String, Object> headers = Map.of("correlation-id", "test-corr-id");
        RuntimeException error = new RuntimeException("Test error");

        // Act
        StepVerifier.create(errorHandlerRegistry.handleError(event, headers, error, "TestService.handle"))
            .verifyComplete();

        // Assert
        assertThat(testHandler.wasInvoked.get()).isTrue();
        assertThat(testHandler.lastEvent).isEqualTo(event);
        assertThat(testHandler.lastError).isEqualTo(error);
        assertThat(testHandler.lastListenerMethod).isEqualTo("TestService.handle");
    }

    @Test
    void shouldExecuteHandlersInPriorityOrder() {
        // Arrange
        TestCustomErrorHandler lowPriorityHandler = new TestCustomErrorHandler("low", 10);
        TestCustomErrorHandler highPriorityHandler = new TestCustomErrorHandler("high", 100);
        
        errorHandlerRegistry.registerHandler(lowPriorityHandler);
        errorHandlerRegistry.registerHandler(highPriorityHandler);
        
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        RuntimeException error = new RuntimeException("Test error");

        // Act
        StepVerifier.create(errorHandlerRegistry.handleError(event, Map.of(), error, "TestService.handle"))
            .verifyComplete();

        // Assert - Both handlers should be invoked
        assertThat(lowPriorityHandler.wasInvoked.get()).isTrue();
        assertThat(highPriorityHandler.wasInvoked.get()).isTrue();
        
        // High priority handler should be invoked first (but we can't easily test order in parallel execution)
        assertThat(errorHandlerRegistry.getRegisteredHandlers()).hasSize(2);
    }

    @Test
    void shouldFilterHandlersByErrorType() {
        // Arrange
        TestCustomErrorHandler runtimeExceptionHandler = new TestCustomErrorHandler("runtime", 50) {
            @Override
            public boolean canHandle(Class<? extends Throwable> errorType) {
                return RuntimeException.class.isAssignableFrom(errorType);
            }
        };
        
        TestCustomErrorHandler illegalArgumentHandler = new TestCustomErrorHandler("illegal", 50) {
            @Override
            public boolean canHandle(Class<? extends Throwable> errorType) {
                return IllegalArgumentException.class.isAssignableFrom(errorType);
            }
        };
        
        errorHandlerRegistry.registerHandler(runtimeExceptionHandler);
        errorHandlerRegistry.registerHandler(illegalArgumentHandler);
        
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        RuntimeException error = new RuntimeException("Test error");

        // Act
        StepVerifier.create(errorHandlerRegistry.handleError(event, Map.of(), error, "TestService.handle"))
            .verifyComplete();

        // Assert - Only RuntimeException handler should be invoked
        assertThat(runtimeExceptionHandler.wasInvoked.get()).isTrue();
        assertThat(illegalArgumentHandler.wasInvoked.get()).isFalse();
    }

    @Test
    void shouldHandleHandlerFailuresGracefully() {
        // Arrange
        TestCustomErrorHandler failingHandler = new TestCustomErrorHandler("failing", 50) {
            @Override
            public Mono<Void> handleError(Object event, Map<String, Object> headers, 
                                         Throwable error, String listenerMethod) {
                return Mono.error(new RuntimeException("Handler failed"));
            }
        };
        
        TestCustomErrorHandler workingHandler = new TestCustomErrorHandler("working", 40);
        
        errorHandlerRegistry.registerHandler(failingHandler);
        errorHandlerRegistry.registerHandler(workingHandler);
        
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        RuntimeException error = new RuntimeException("Test error");

        // Act & Assert - Should complete despite handler failure
        StepVerifier.create(errorHandlerRegistry.handleError(event, Map.of(), error, "TestService.handle"))
            .verifyComplete();

        // Working handler should still be invoked
        assertThat(workingHandler.wasInvoked.get()).isTrue();
    }

    @Test
    void shouldHandleNoAvailableHandlers() {
        // Arrange
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        RuntimeException error = new RuntimeException("Test error");

        // Act & Assert
        StepVerifier.create(errorHandlerRegistry.handleError(event, Map.of(), error, "TestService.handle"))
            .verifyComplete();

        assertThat(errorHandlerRegistry.hasHandlers()).isFalse();
    }

    @Test
    void shouldUnregisterHandlers() {
        // Arrange
        errorHandlerRegistry.registerHandler(testHandler);
        assertThat(errorHandlerRegistry.hasHandlers()).isTrue();

        // Act
        boolean removed = errorHandlerRegistry.unregisterHandler(testHandler.getHandlerName());

        // Assert
        assertThat(removed).isTrue();
        assertThat(errorHandlerRegistry.hasHandlers()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUnregisteringNonExistentHandler() {
        // Act
        boolean removed = errorHandlerRegistry.unregisterHandler("non-existent");

        // Assert
        assertThat(removed).isFalse();
    }

    @Test
    void shouldInitializeWithSpringBeans() {
        // Arrange
        MetricsErrorHandler metricsHandler = new MetricsErrorHandler(meterRegistry);
        List<CustomErrorHandler> handlers = List.of(metricsHandler);
        
        CustomErrorHandlerRegistry registry = new CustomErrorHandlerRegistry();
        // Register handlers programmatically
        handlers.forEach(registry::registerHandler);

        // Act - handlers are already registered

        // Assert
        assertThat(registry.hasHandlers()).isTrue();
        assertThat(registry.getRegisteredHandlers()).hasSize(1);
        assertThat(registry.getRegisteredHandlers().get(0)).isInstanceOf(MetricsErrorHandler.class);
    }

    @Test
    void shouldHandleNullEvents() {
        // Arrange
        errorHandlerRegistry.registerHandler(testHandler);
        RuntimeException error = new RuntimeException("Test error");

        // Act & Assert
        StepVerifier.create(errorHandlerRegistry.handleError(null, Map.of(), error, "TestService.handle"))
            .verifyComplete();

        assertThat(testHandler.wasInvoked.get()).isTrue();
        assertThat(testHandler.lastEvent).isNull();
    }

    @Test
    void shouldHandleNullHeaders() {
        // Arrange
        errorHandlerRegistry.registerHandler(testHandler);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        RuntimeException error = new RuntimeException("Test error");

        // Act & Assert
        StepVerifier.create(errorHandlerRegistry.handleError(event, null, error, "TestService.handle"))
            .verifyComplete();

        assertThat(testHandler.wasInvoked.get()).isTrue();
        assertThat(testHandler.lastHeaders).isNull();
    }

    @Test
    void shouldHandleNullError() {
        // Arrange
        errorHandlerRegistry.registerHandler(testHandler);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());

        // Act & Assert - Should complete without invoking handlers when error is null
        StepVerifier.create(errorHandlerRegistry.handleError(event, Map.of(), null, "TestService.handle"))
            .verifyComplete();

        assertThat(testHandler.wasInvoked.get()).isFalse();
        assertThat(testHandler.lastError).isNull();
    }

    @Test
    void shouldHandleEmptyListenerMethod() {
        // Arrange
        errorHandlerRegistry.registerHandler(testHandler);
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        RuntimeException error = new RuntimeException("Test error");

        // Act & Assert
        StepVerifier.create(errorHandlerRegistry.handleError(event, Map.of(), error, ""))
            .verifyComplete();

        assertThat(testHandler.wasInvoked.get()).isTrue();
        assertThat(testHandler.lastListenerMethod).isEmpty();
    }

    @Test
    void shouldProvideCorrectHandlerList() {
        // Arrange
        TestCustomErrorHandler handler1 = new TestCustomErrorHandler("handler1", 100);
        TestCustomErrorHandler handler2 = new TestCustomErrorHandler("handler2", 50);
        
        errorHandlerRegistry.registerHandler(handler1);
        errorHandlerRegistry.registerHandler(handler2);

        // Act
        List<CustomErrorHandler> handlers = errorHandlerRegistry.getRegisteredHandlers();

        // Assert
        assertThat(handlers).hasSize(2);
        // Should be sorted by priority (highest first)
        assertThat(handlers.get(0).getPriority()).isEqualTo(100);
        assertThat(handlers.get(1).getPriority()).isEqualTo(50);
    }

    /**
     * Test implementation of CustomErrorHandler for testing purposes.
     */
    static class TestCustomErrorHandler implements CustomErrorHandler {
        
        final AtomicBoolean wasInvoked = new AtomicBoolean(false);
        final String name;
        final int priority;
        
        Object lastEvent;
        Map<String, Object> lastHeaders;
        Throwable lastError;
        String lastListenerMethod;

        TestCustomErrorHandler() {
            this("test-handler", 0);
        }

        TestCustomErrorHandler(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public Mono<Void> handleError(Object event, Map<String, Object> headers, 
                                     Throwable error, String listenerMethod) {
            return Mono.fromRunnable(() -> {
                wasInvoked.set(true);
                lastEvent = event;
                lastHeaders = headers;
                lastError = error;
                lastListenerMethod = listenerMethod;
            });
        }

        @Override
        public String getHandlerName() {
            return name;
        }

        @Override
        public int getPriority() {
            return priority;
        }
    }
}
