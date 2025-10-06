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

package com.firefly.common.eda.metrics;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for EDA library using Micrometer.
 * <p>
 * This component provides comprehensive metrics for:
 * <ul>
 *   <li>Event publishing - success/failure counts, latency, message sizes</li>
 *   <li>Event consumption - processing times, failure rates, throughput</li>
 *   <li>Publisher health and availability</li>
 *   <li>Consumer health and processing statistics</li>
 *   <li>Circuit breaker and resilience metrics</li>
 * </ul>
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.eda", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EdaMetrics {

    private static final String METRIC_PREFIX = "firefly.eda";
    
    private final MeterRegistry meterRegistry;
    
    // Caches for meters to avoid recreation
    private final ConcurrentMap<String, Timer> publishTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> consumeTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> publishSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> publishFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> consumeSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> consumeFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DistributionSummary> messageSizeSummaries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> publisherHealthGauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> consumerHealthGauges = new ConcurrentHashMap<>();

    // Publishing metrics

    /**
     * Records a successful event publication.
     *
     * @param publisherType the type of publisher (kafka, rabbitmq, etc.)
     * @param destination the destination (topic, queue, etc.)
     * @param eventType the type of event published
     * @param duration the time taken to publish
     * @param messageSize the size of the message in bytes
     */
    public void recordPublishSuccess(String publisherType, String destination, String eventType, 
                                     Duration duration, long messageSize) {
        try {
            String timerKey = publisherType + ":" + destination + ":" + eventType;
            Timer timer = publishTimers.computeIfAbsent(timerKey, 
                    key -> Timer.builder(METRIC_PREFIX + ".publish.duration")
                            .tag("publisher_type", publisherType)
                            .tag("destination", destination)
                            .tag("event_type", eventType)
                            .tag("status", "success")
                            .description("Time taken to publish an event")
                            .register(meterRegistry));
            timer.record(duration);

            String successCounterKey = publisherType + ":" + destination;
            Counter successCounter = publishSuccessCounters.computeIfAbsent(successCounterKey,
                    key -> Counter.builder(METRIC_PREFIX + ".publish.count")
                            .tag("publisher_type", publisherType)
                            .tag("destination", destination)
                            .tag("status", "success")
                            .description("Number of successfully published events")
                            .register(meterRegistry));
            successCounter.increment();

            String sizeSummaryKey = publisherType + ":" + destination;
            DistributionSummary sizeSummary = messageSizeSummaries.computeIfAbsent(sizeSummaryKey,
                    key -> DistributionSummary.builder(METRIC_PREFIX + ".publish.message.size")
                            .tag("publisher_type", publisherType)
                            .tag("destination", destination)
                            .description("Size of published messages in bytes")
                            .register(meterRegistry));
            sizeSummary.record(messageSize);

        } catch (Exception e) {
            log.warn("Error recording publish success metrics: {}", e.getMessage());
        }
    }

    /**
     * Records a failed event publication.
     *
     * @param publisherType the type of publisher
     * @param destination the destination
     * @param eventType the type of event
     * @param duration the time taken before failure
     * @param errorType the type/category of error
     */
    public void recordPublishFailure(String publisherType, String destination, String eventType,
                                     Duration duration, String errorType) {
        try {
            String timerKey = publisherType + ":" + destination + ":" + eventType + ":failure";
            Timer timer = publishTimers.computeIfAbsent(timerKey,
                    key -> Timer.builder(METRIC_PREFIX + ".publish.duration")
                            .tag("publisher_type", publisherType)
                            .tag("destination", destination)
                            .tag("event_type", eventType)
                            .tag("status", "failure")
                            .description("Time taken before publish failure")
                            .register(meterRegistry));
            timer.record(duration);

            String failureCounterKey = publisherType + ":" + destination + ":" + errorType;
            Counter failureCounter = publishFailureCounters.computeIfAbsent(failureCounterKey,
                    key -> Counter.builder(METRIC_PREFIX + ".publish.count")
                            .tag("publisher_type", publisherType)
                            .tag("destination", destination)
                            .tag("error_type", errorType)
                            .tag("status", "failure")
                            .description("Number of failed event publications")
                            .register(meterRegistry));
            failureCounter.increment();

        } catch (Exception e) {
            log.warn("Error recording publish failure metrics: {}", e.getMessage());
        }
    }

    // Consumption metrics

    /**
     * Records a successful event consumption.
     *
     * @param consumerType the type of consumer
     * @param source the source (topic, queue, etc.)
     * @param eventType the type of event consumed
     * @param processingDuration the time taken to process
     */
    public void recordConsumeSuccess(String consumerType, String source, String eventType, 
                                     Duration processingDuration) {
        try {
            String timerKey = consumerType + ":" + source + ":" + eventType;
            Timer timer = consumeTimers.computeIfAbsent(timerKey,
                    key -> Timer.builder(METRIC_PREFIX + ".consume.duration")
                            .tag("consumer_type", consumerType)
                            .tag("source", source)
                            .tag("event_type", eventType)
                            .tag("status", "success")
                            .description("Time taken to process a consumed event")
                            .register(meterRegistry));
            timer.record(processingDuration);

            String successCounterKey = consumerType + ":" + source;
            Counter successCounter = consumeSuccessCounters.computeIfAbsent(successCounterKey,
                    key -> Counter.builder(METRIC_PREFIX + ".consume.count")
                            .tag("consumer_type", consumerType)
                            .tag("source", source)
                            .tag("status", "success")
                            .description("Number of successfully processed events")
                            .register(meterRegistry));
            successCounter.increment();

        } catch (Exception e) {
            log.warn("Error recording consume success metrics: {}", e.getMessage());
        }
    }

    /**
     * Records a failed event consumption.
     *
     * @param consumerType the type of consumer
     * @param source the source
     * @param eventType the type of event
     * @param processingDuration the time taken before failure
     * @param errorType the type/category of error
     */
    public void recordConsumeFailure(String consumerType, String source, String eventType,
                                     Duration processingDuration, String errorType) {
        try {
            String timerKey = consumerType + ":" + source + ":" + eventType + ":failure";
            Timer timer = consumeTimers.computeIfAbsent(timerKey,
                    key -> Timer.builder(METRIC_PREFIX + ".consume.duration")
                            .tag("consumer_type", consumerType)
                            .tag("source", source)
                            .tag("event_type", eventType)
                            .tag("status", "failure")
                            .description("Time taken before consume failure")
                            .register(meterRegistry));
            timer.record(processingDuration);

            String failureCounterKey = consumerType + ":" + source + ":" + errorType;
            Counter failureCounter = consumeFailureCounters.computeIfAbsent(failureCounterKey,
                    key -> Counter.builder(METRIC_PREFIX + ".consume.count")
                            .tag("consumer_type", consumerType)
                            .tag("source", source)
                            .tag("error_type", errorType)
                            .tag("status", "failure")
                            .description("Number of failed event consumptions")
                            .register(meterRegistry));
            failureCounter.increment();

        } catch (Exception e) {
            log.warn("Error recording consume failure metrics: {}", e.getMessage());
        }
    }

    // Health metrics

    /**
     * Updates publisher health status.
     *
     * @param publisherType the type of publisher
     * @param connectionId the connection ID
     * @param isHealthy whether the publisher is healthy (1) or not (0)
     */
    public void updatePublisherHealth(String publisherType, String connectionId, boolean isHealthy) {
        try {
            String gaugeKey = publisherType + ":" + connectionId;
            AtomicLong gauge = publisherHealthGauges.computeIfAbsent(gaugeKey, key -> {
                AtomicLong gaugeValue = new AtomicLong(0);
                Gauge.builder(METRIC_PREFIX + ".publisher.health", gaugeValue, AtomicLong::get)
                        .tag("publisher_type", publisherType)
                        .tag("connection_id", connectionId)
                        .description("Publisher health status (1=healthy, 0=unhealthy)")
                        .register(meterRegistry);
                return gaugeValue;
            });
            gauge.set(isHealthy ? 1 : 0);

        } catch (Exception e) {
            log.warn("Error updating publisher health metrics: {}", e.getMessage());
        }
    }

    /**
     * Updates consumer health status.
     *
     * @param consumerType the type of consumer
     * @param isHealthy whether the consumer is healthy (1) or not (0)
     */
    public void updateConsumerHealth(String consumerType, boolean isHealthy) {
        try {
            AtomicLong gauge = consumerHealthGauges.computeIfAbsent(consumerType, key -> {
                AtomicLong gaugeValue = new AtomicLong(0);
                Gauge.builder(METRIC_PREFIX + ".consumer.health", gaugeValue, AtomicLong::get)
                        .tag("consumer_type", consumerType)
                        .description("Consumer health status (1=healthy, 0=unhealthy)")
                        .register(meterRegistry);
                return gaugeValue;
            });
            gauge.set(isHealthy ? 1 : 0);

        } catch (Exception e) {
            log.warn("Error updating consumer health metrics: {}", e.getMessage());
        }
    }

    // Circuit breaker metrics

    /**
     * Records circuit breaker state change.
     *
     * @param publisherType the type of publisher
     * @param state the circuit breaker state (CLOSED, OPEN, HALF_OPEN)
     */
    public void recordCircuitBreakerStateChange(String publisherType, String state) {
        try {
            Counter.builder(METRIC_PREFIX + ".circuit.breaker.state.change")
                    .tag("publisher_type", publisherType)
                    .tag("state", state)
                    .description("Circuit breaker state changes")
                    .register(meterRegistry)
                    .increment();

        } catch (Exception e) {
            log.warn("Error recording circuit breaker metrics: {}", e.getMessage());
        }
    }

    /**
     * Records retry attempt.
     *
     * @param publisherType the type of publisher
     * @param attempt the attempt number
     * @param successful whether the retry was successful
     */
    public void recordRetryAttempt(String publisherType, int attempt, boolean successful) {
        try {
            Counter.builder(METRIC_PREFIX + ".retry.attempt")
                    .tag("publisher_type", publisherType)
                    .tag("attempt", String.valueOf(attempt))
                    .tag("status", successful ? "success" : "failure")
                    .description("Retry attempts")
                    .register(meterRegistry)
                    .increment();

        } catch (Exception e) {
            log.warn("Error recording retry metrics: {}", e.getMessage());
        }
    }

    /**
     * Records rate limiter rejection.
     *
     * @param publisherType the type of publisher
     */
    public void recordRateLimiterRejection(String publisherType) {
        try {
            Counter.builder(METRIC_PREFIX + ".rate.limiter.rejection")
                    .tag("publisher_type", publisherType)
                    .description("Rate limiter rejections")
                    .register(meterRegistry)
                    .increment();

        } catch (Exception e) {
            log.warn("Error recording rate limiter metrics: {}", e.getMessage());
        }
    }
}