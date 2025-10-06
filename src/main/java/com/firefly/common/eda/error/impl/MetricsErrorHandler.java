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

package com.firefly.common.eda.error.impl;

import com.firefly.common.eda.error.CustomErrorHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom error handler that records error metrics.
 * <p>
 * This handler demonstrates how to integrate custom error handling
 * with metrics collection for monitoring and observability.
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.eda.error.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MetricsErrorHandler implements CustomErrorHandler {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> errorTimers = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handleError(Object event, Map<String, Object> headers,
                                 Throwable error, String listenerMethod) {
        return Mono.fromRunnable(() -> {
            recordErrorMetrics(event, headers, error, listenerMethod);
        });
    }

    @Override
    public String getHandlerName() {
        return "metrics-error-handler";
    }

    @Override
    public int getPriority() {
        return 50; // Medium priority for metrics
    }

    private void recordErrorMetrics(Object event, Map<String, Object> headers, Throwable error, String listenerMethod) {
        try {
            // Record error count by type and listener method
            String errorType = error.getClass().getSimpleName();
            String eventType = event.getClass().getSimpleName();
            
            // Error counter by listener method and error type
            String counterKey = String.format("eda.listener.errors.%s.%s", listenerMethod, errorType);
            Counter counter = errorCounters.computeIfAbsent(counterKey, key ->
                Counter.builder("eda.listener.errors")
                       .description("Number of errors in event listeners")
                       .tag("listener.method", listenerMethod)
                       .tag("error.type", errorType)
                       .tag("event.type", eventType)
                       .register(meterRegistry)
            );
            counter.increment();

            // Record error timing (if available in headers)
            Object startTime = headers.get("processing.start.time");
            if (startTime instanceof Long) {
                long duration = System.currentTimeMillis() - (Long) startTime;
                String timerKey = String.format("eda.listener.error.duration.%s", listenerMethod);
                Timer timer = errorTimers.computeIfAbsent(timerKey, key ->
                    Timer.builder("eda.listener.error.duration")
                         .description("Duration of failed event processing")
                         .tag("listener.method", listenerMethod)
                         .tag("error.type", errorType)
                         .register(meterRegistry)
                );
                timer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            log.debug("Recorded error metrics for {} in {}: {}", 
                     errorType, listenerMethod, error.getMessage());

        } catch (Exception e) {
            log.warn("Failed to record error metrics", e);
        }
    }
}
