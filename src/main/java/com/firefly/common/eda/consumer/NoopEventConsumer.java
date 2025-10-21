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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * No-operation event consumer that doesn't consume any messages.
 * <p>
 * This consumer is useful for:
 * <ul>
 *   <li>Testing scenarios where message consumption should be disabled</li>
 *   <li>Development environments without messaging infrastructure</li>
 *   <li>Fallback when all other consumers are unavailable</li>
 * </ul>
 * <p>
 * All consume operations return empty streams and perform no actual work.
 */
@Component
@ConditionalOnProperty(prefix = "firefly.eda.consumer.noop", name = "enabled", havingValue = "true")
@Slf4j
public class NoopEventConsumer implements EventConsumer {

    private volatile boolean running = false;

    @Override
    public Flux<EventEnvelope> consume() {
        log.debug("NOOP consumer: returning empty flux for consume()");
        return Flux.empty();
    }

    @Override
    public Flux<EventEnvelope> consume(String... destinations) {
        log.debug("NOOP consumer: returning empty flux for consume() with destinations: {}", 
                 destinations != null ? String.join(", ", destinations) : "none");
        return Flux.empty();
    }

    @Override
    public Mono<Void> start() {
        log.debug("NOOP consumer: starting (no-op)");
        running = true;
        return Mono.empty();
    }

    @Override
    public Mono<Void> stop() {
        log.debug("NOOP consumer: stopping (no-op)");
        running = false;
        return Mono.empty();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getConsumerType() {
        return "NOOP";
    }

    @Override
    public boolean isAvailable() {
        return true; // NOOP consumer is always available
    }

    @Override
    public Mono<ConsumerHealth> getHealth() {
        return Mono.just(ConsumerHealth.builder()
                .consumerType(getConsumerType())
                .available(true)
                .running(isRunning())
                .status("UP")
                .details(Map.of(
                        "description", "No-operation consumer that doesn't consume any messages",
                        "suitable_for", "testing, development, fallback scenarios"
                ))
                .build());
    }
}
