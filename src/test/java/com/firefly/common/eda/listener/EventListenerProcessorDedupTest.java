/*
 * Copyright 2025 Firefly Software Solutions Inc
 */
package com.firefly.common.eda.listener;

import com.firefly.common.eda.annotation.EventListener;
import com.firefly.common.eda.dedup.DeduplicationService;
import com.firefly.common.eda.error.CustomErrorHandlerRegistry;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventListenerProcessorDedupTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private CustomErrorHandlerRegistry customErrorHandlerRegistry;

    @Mock
    private Environment environment;

    private EventListenerProcessor processor;
    private CountingListenerBean countingBean;

    @BeforeEach
    void setup() {
        countingBean = new CountingListenerBean();

        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{"countingBean"});
        when(applicationContext.getBean("countingBean")).thenReturn(countingBean);
        lenient().when(environment.resolvePlaceholders(any())).thenAnswer(inv -> inv.getArgument(0));

        processor = new EventListenerProcessor(applicationContext, applicationEventPublisher, customErrorHandlerRegistry, environment);
        processor.initializeEventListeners();

        // Inject mock dedup service
        DeduplicationService dedupMock = mock(DeduplicationService.class);
        when(dedupMock.tryAcquire(any(), any()))
                .thenReturn(Mono.just(true))
                .thenReturn(Mono.just(false));

        processor.setDeduplicationService(dedupMock);
    }

    @Test
    void shouldProcessEventOnlyOnceWhenDuplicate() {
        TestEventModels.SimpleTestEvent event = new TestEventModels.SimpleTestEvent("id-1", "payload", Instant.now());
        Map<String, Object> headers = Map.of("transaction-id", "tx-1");

        StepVerifier.create(processor.processEvent(event, headers)).verifyComplete();
        StepVerifier.create(processor.processEvent(event, headers)).verifyComplete();

        assertThat(countingBean.count.get()).isEqualTo(1);
    }

    static class CountingListenerBean {
        final AtomicInteger count = new AtomicInteger(0);

        @EventListener(eventTypes = {"SimpleTestEvent"})
        public void onSimple(TestEventModels.SimpleTestEvent event) {
            count.incrementAndGet();
        }
    }
}
