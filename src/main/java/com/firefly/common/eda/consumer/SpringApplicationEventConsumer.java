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

package com.firefly.common.eda.consumer;

import com.firefly.common.eda.event.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Application Event consumer implementation.
 * <p>
 * This consumer receives events from Spring's ApplicationEventPublisher
 * and emits them as a reactive stream for processing by application event handlers.
 * <p>
 * It listens for EventEnvelope instances published through Spring's event mechanism
 * and bridges them to the reactive streams API.
 */
@Component
@ConditionalOnProperty(prefix = "firefly.eda.consumer.application-event", name = "enabled", havingValue = "true")
@Slf4j
public class SpringApplicationEventConsumer implements EventConsumer {

    private final Sinks.Many<EventEnvelope> eventSink = Sinks.many().multicast().directBestEffort();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public Flux<EventEnvelope> consume() {
        return eventSink.asFlux()
                .doOnSubscribe(subscription -> {
                    if (!running.get()) {
                        start().subscribe();
                    }
                })
                .doOnNext(envelope -> 
                    log.debug("📥 [Spring Application Event Consumer] Emitting event: destination={}, type={}", 
                             envelope.destination(), envelope.eventType()));
    }

    @Override
    public Flux<EventEnvelope> consume(String... destinations) {
        return consume()
                .filter(envelope -> {
                    if (destinations == null || destinations.length == 0) {
                        return true;
                    }
                    return Arrays.stream(destinations)
                            .anyMatch(dest -> dest.equals(envelope.destination()));
                })
                .doOnNext(envelope -> 
                    log.debug("📥 [Spring Application Event Consumer] Filtered event for destinations {}: destination={}, type={}", 
                             Arrays.toString(destinations), envelope.destination(), envelope.eventType()));
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("🚀 [Spring Application Event Consumer] Starting consumer");
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("🛑 [Spring Application Event Consumer] Stopping consumer");
                // Don't complete the sink to allow for restart
            }
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getConsumerType() {
        return "APPLICATION_EVENT";
    }

    @Override
    public boolean isAvailable() {
        return true; // Spring Application Event consumer is always available in Spring context
    }

    @Override
    public Mono<ConsumerHealth> getHealth() {
        return Mono.just(ConsumerHealth.builder()
                .consumerType(getConsumerType())
                .available(true)
                .running(isRunning())
                .status(isRunning() ? "UP" : "DOWN")
                .details(Map.of(
                        "description", "Spring Application Event consumer for in-memory event processing",
                        "event_sink_subscribers", eventSink.currentSubscriberCount(),
                        "suitable_for", "single-instance applications, testing, internal communication"
                ))
                .build());
    }

    /**
     * Event listener method to receive Spring Application Events.
     * <p>
     * This method is automatically called by Spring when EventEnvelope instances
     * are published through the ApplicationEventPublisher.
     */
    @EventListener
    public void onApplicationEvent(EventEnvelope event) {
        log.debug("📥 [Spring Application Event Consumer] Received event: destination={}, type={}, running={}",
                 event.destination(), event.eventType(), running.get());

        if (running.get()) {
            Sinks.EmitResult result = eventSink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("⚠️ [Spring Application Event Consumer] Failed to emit event: {}, result: {}",
                        event.eventType(), result);
            }
        } else {
            log.debug("🚫 [Spring Application Event Consumer] Ignoring event (consumer not running): destination={}, type={}",
                     event.destination(), event.eventType());
        }
    }

    @Override
    public void close() {
        stop().subscribe();
    }
}
