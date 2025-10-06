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

package com.firefly.common.eda.listener;

import com.firefly.common.eda.annotation.ErrorHandlingStrategy;
import com.firefly.common.eda.annotation.EventListener;
import com.firefly.common.eda.error.CustomErrorHandlerRegistry;
import com.firefly.common.eda.testconfig.TestEventModels.SimpleTestEvent;
import com.firefly.common.eda.testconfig.TestEventModels.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for EventListenerProcessor functionality.
 * <p>
 * Tests event listener discovery, method invocation, error handling strategies,
 * and event processing workflows.
 */
@ExtendWith(MockitoExtension.class)
class EventListenerProcessorTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private CustomErrorHandlerRegistry customErrorHandlerRegistry;

    private EventListenerProcessor eventListenerProcessor;
    private TestEventListenerBean testBean;

    @BeforeEach
    void setUp() {
        testBean = new TestEventListenerBean();

        when(applicationContext.getBeanDefinitionNames())
            .thenReturn(new String[]{"testBean"});
        when(applicationContext.getBean("testBean"))
            .thenReturn(testBean);

        eventListenerProcessor = new EventListenerProcessor(
            applicationContext, applicationEventPublisher, customErrorHandlerRegistry
        );
    }

    @Test
    void shouldDiscoverEventListenerMethods() {
        // Act
        eventListenerProcessor.initializeEventListeners();

        // Assert - Should discover methods annotated with @EventListener
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());
        Map<String, Object> headers = Map.of("test-header", "test-value");

        StepVerifier.create(eventListenerProcessor.processEvent(event, headers))
            .verifyComplete();

        assertThat(testBean.simpleEventProcessed.get()).isTrue();
    }

    @Test
    void shouldInvokeCorrectListenerBasedOnEventType() {
        // Arrange
        eventListenerProcessor.initializeEventListeners();
        
        SimpleTestEvent simpleEvent = new SimpleTestEvent("test-id", "test message", Instant.now());
        OrderCreatedEvent orderEvent = new OrderCreatedEvent("order-123", "customer-456", 100.0, "USD", Instant.now());

        // Act & Assert - Simple event
        StepVerifier.create(eventListenerProcessor.processEvent(simpleEvent, Map.of()))
            .verifyComplete();

        assertThat(testBean.simpleEventProcessed.get()).isTrue();
        assertThat(testBean.orderEventProcessed.get()).isFalse();

        // Reset and test order event
        testBean.reset();

        StepVerifier.create(eventListenerProcessor.processEvent(orderEvent, Map.of()))
            .verifyComplete();

        assertThat(testBean.simpleEventProcessed.get()).isFalse();
        assertThat(testBean.orderEventProcessed.get()).isTrue();
    }

    @Test
    void shouldHandleErrorsAccordingToStrategy() {
        // Arrange
        eventListenerProcessor.initializeEventListeners();
        testBean.shouldThrowError = true;

        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());

        // Act & Assert - Should handle error gracefully with LOG_AND_CONTINUE strategy
        StepVerifier.create(eventListenerProcessor.processEvent(event, Map.of()))
            .verifyComplete();

        assertThat(testBean.errorThrown.get()).isTrue();
    }

    @Test
    void shouldHandleCustomErrorStrategy() {
        // Arrange
        when(customErrorHandlerRegistry.hasHandlers()).thenReturn(true);
        when(customErrorHandlerRegistry.handleError(any(), any(), any(), any()))
            .thenReturn(Mono.empty());

        eventListenerProcessor.initializeEventListeners();
        testBean.shouldThrowErrorInCustomHandler = true;

        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());

        // Act & Assert
        StepVerifier.create(eventListenerProcessor.processEvent(event, Map.of()))
            .verifyComplete();

        verify(customErrorHandlerRegistry).handleError(eq(event), any(Map.class), any(RuntimeException.class), any(String.class));
    }

    @Test
    void shouldProcessMultipleListenersForSameEventType() {
        // Arrange
        eventListenerProcessor.initializeEventListeners();
        
        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());

        // Act
        StepVerifier.create(eventListenerProcessor.processEvent(event, Map.of()))
            .verifyComplete();

        // Assert - Both listeners should be invoked
        assertThat(testBean.simpleEventProcessed.get()).isTrue();
        assertThat(testBean.anotherSimpleEventProcessed.get()).isTrue();
    }

    @Test
    void shouldSendToDeadLetterQueueOnDeadLetterStrategy() {
        // Arrange
        eventListenerProcessor.initializeEventListeners();
        testBean.shouldThrowErrorInDeadLetterHandler = true;

        SimpleTestEvent event = new SimpleTestEvent("test-id", "test message", Instant.now());

        // Act
        StepVerifier.create(eventListenerProcessor.processEvent(event, Map.of()))
            .verifyComplete();

        // Assert - Should publish dead letter queue event
        verify(applicationEventPublisher).publishEvent(any(DeadLetterQueueEvent.class));
    }

    /**
     * Test bean with various @EventListener annotated methods for testing.
     */
    static class TestEventListenerBean {
        
        final AtomicBoolean simpleEventProcessed = new AtomicBoolean(false);
        final AtomicBoolean anotherSimpleEventProcessed = new AtomicBoolean(false);
        final AtomicBoolean orderEventProcessed = new AtomicBoolean(false);
        final AtomicBoolean errorThrown = new AtomicBoolean(false);
        
        boolean shouldThrowError = false;
        boolean shouldThrowErrorInCustomHandler = false;
        boolean shouldThrowErrorInDeadLetterHandler = false;

        @EventListener(eventTypes = {"SimpleTestEvent"}, errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE)
        public void handleSimpleEvent(SimpleTestEvent event) {
            if (shouldThrowError) {
                errorThrown.set(true);
                throw new RuntimeException("Test error");
            }
            simpleEventProcessed.set(true);
        }

        @EventListener(eventTypes = {"SimpleTestEvent"}, errorStrategy = ErrorHandlingStrategy.CUSTOM)
        public void handleSimpleEventWithCustomError(SimpleTestEvent event) {
            if (shouldThrowErrorInCustomHandler) {
                throw new RuntimeException("Custom error test");
            }
            anotherSimpleEventProcessed.set(true);
        }

        @EventListener(eventTypes = {"SimpleTestEvent"}, errorStrategy = ErrorHandlingStrategy.DEAD_LETTER)
        public void handleSimpleEventWithDeadLetter(SimpleTestEvent event) {
            if (shouldThrowErrorInDeadLetterHandler) {
                throw new RuntimeException("Dead letter test error");
            }
        }

        @EventListener(eventTypes = {"OrderCreatedEvent"})
        public void handleOrderCreated(OrderCreatedEvent event) {
            orderEventProcessed.set(true);
        }

        void reset() {
            simpleEventProcessed.set(false);
            anotherSimpleEventProcessed.set(false);
            orderEventProcessed.set(false);
            errorThrown.set(false);
            shouldThrowError = false;
            shouldThrowErrorInCustomHandler = false;
            shouldThrowErrorInDeadLetterHandler = false;
        }
    }
}
