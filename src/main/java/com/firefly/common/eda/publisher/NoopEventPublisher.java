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

package com.firefly.common.eda.publisher;

import com.firefly.common.eda.annotation.PublisherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * No-operation event publisher that discards all messages.
 * <p>
 * This publisher is useful for:
 * <ul>
 *   <li>Testing scenarios where messaging should be disabled</li>
 *   <li>Development environments without messaging infrastructure</li>
 *   <li>Fallback when all other publishers are unavailable</li>
 * </ul>
 * <p>
 * All publish operations complete successfully but perform no actual work.
 */
@Component
@ConditionalOnProperty(prefix = "firefly.eda.publishers.noop", name = "enabled", havingValue = "true")
@Slf4j
public class NoopEventPublisher implements EventPublisher {

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        log.debug("NOOP publisher: discarding event - destination={}, event={}, headers={}", 
                 destination, event != null ? event.getClass().getSimpleName() : "null", 
                 headers != null ? headers.keySet() : "none");
        return Mono.empty();
    }

    @Override
    public boolean isAvailable() {
        return true; // NOOP publisher is always available
    }

    @Override
    public PublisherType getPublisherType() {
        return PublisherType.NOOP;
    }

    @Override
    public String getDefaultDestination() {
        return "noop-destination";
    }

    @Override
    public Mono<PublisherHealth> getHealth() {
        return Mono.just(PublisherHealth.builder()
                .publisherType(getPublisherType())
                .available(true)
                .status("UP")
                .details(Map.of(
                        "description", "No-operation publisher that discards all messages",
                        "suitable_for", "testing, development, fallback scenarios"
                ))
                .build());
    }
}