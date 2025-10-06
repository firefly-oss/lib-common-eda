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

import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Handler for dead letter queue events.
 * <p>
 * This component handles DeadLetterQueueEvent instances by publishing
 * the failed events to the appropriate dead letter queue using the
 * EventPublisherFactory. This design breaks the circular dependency
 * between EventListenerProcessor and EventPublisherFactory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueHandler {

    private final EventPublisherFactory eventPublisherFactory;

    /**
     * Handles dead letter queue events by publishing them to the appropriate destination.
     * <p>
     * This method is executed asynchronously to avoid blocking the original event processing.
     */
    @EventListener
    @Async
    public void handleDeadLetterQueueEvent(DeadLetterQueueEvent dlqEvent) {
        try {
            log.info("Processing dead letter queue event for destination: {}", 
                    dlqEvent.getDeadLetterDestination());

            // Get the default publisher for dead letter queue
            EventPublisher publisher = eventPublisherFactory.getDefaultPublisher();

            if (publisher == null || !publisher.isAvailable()) {
                log.warn("Cannot send to dead letter queue - no available publisher");
                return;
            }

            // Publish the failed event to the dead letter queue
            publisher.publish(
                    dlqEvent.getOriginalEvent(),
                    dlqEvent.getDeadLetterDestination(),
                    dlqEvent.createDeadLetterHeaders()
            )
            .doOnSuccess(v -> log.info("Successfully sent event to dead letter queue: {}", 
                                     dlqEvent.getDeadLetterDestination()))
            .doOnError(e -> log.error("Failed to send event to dead letter queue: {}", 
                                    dlqEvent.getDeadLetterDestination(), e))
            .onErrorResume(e -> {
                // Log the error but don't propagate it to avoid affecting other processing
                log.error("Dead letter queue publishing failed, event will be lost", e);
                return reactor.core.publisher.Mono.empty();
            })
            .subscribe();

        } catch (Exception e) {
            log.error("Error handling dead letter queue event", e);
        }
    }
}
