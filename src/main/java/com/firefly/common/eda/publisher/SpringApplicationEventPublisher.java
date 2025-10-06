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
import com.firefly.common.eda.event.EventEnvelope;
import com.firefly.common.eda.properties.EdaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Event publisher implementation that uses Spring Application Events.
 * <p>
 * This publisher publishes events synchronously within the same JVM using Spring's
 * application event mechanism. It's suitable for single-instance applications,
 * testing, and scenarios where external messaging infrastructure is not available.
 */
@Component
@ConditionalOnProperty(prefix = "firefly.eda.publishers.application-event", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SpringApplicationEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher springEventPublisher;
    private final EdaProperties edaProperties;

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        return Mono.fromRunnable(() -> {
            try {
                // Create event envelope using unified EventEnvelope
                EventEnvelope eventEnvelope = EventEnvelope.forPublishing(
                        destination != null ? destination : getDefaultDestination(),
                        event != null ? event.getClass().getSimpleName() : "unknown",
                        event,
                        headers != null ? (String) headers.get("transaction-id") : null,
                        headers != null ? headers : Map.of(),
                        EventEnvelope.EventMetadata.empty(), // metadata
                        Instant.now(),
                        getPublisherType().name(),
                        "default" // connectionId
                );

                log.debug("Publishing application event: destination={}, type={}, transactionId={}",
                         eventEnvelope.destination(), eventEnvelope.eventType(), eventEnvelope.transactionId());

                // Publish the event synchronously
                springEventPublisher.publishEvent(eventEnvelope);

                log.debug("Successfully published application event: destination={}, type={}",
                         eventEnvelope.destination(), eventEnvelope.eventType());

            } catch (Exception e) {
                log.error("Failed to publish application event: destination={}, error={}", 
                         destination, e.getMessage(), e);
                throw e;
            }
        }).then();
    }
    
    @Override
    public Mono<Void> publish(Object event, String destination) {
        return publish(event, destination, null);
    }

    @Override
    public boolean isAvailable() {
        return springEventPublisher != null && 
               edaProperties.getPublishers().getApplicationEvent().isEnabled();
    }

    @Override
    public PublisherType getPublisherType() {
        return PublisherType.APPLICATION_EVENT;
    }

    @Override
    public String getDefaultDestination() {
        return edaProperties.getPublishers().getApplicationEvent().getDefaultDestination();
    }

    @Override
    public Mono<PublisherHealth> getHealth() {
        return Mono.just(PublisherHealth.builder()
                .publisherType(getPublisherType())
                .available(isAvailable())
                .status(isAvailable() ? "UP" : "DOWN")
                .details(Map.of(
                        "springEventPublisher", springEventPublisher != null ? "available" : "not available",
                        "enabled", edaProperties.getPublishers().getApplicationEvent().isEnabled(),
                        "defaultDestination", getDefaultDestination()
                ))
                .build());
    }

}