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

package com.firefly.common.eda.consumer.kafka;

import com.firefly.common.eda.consumer.ConsumerHealth;
import com.firefly.common.eda.consumer.EventConsumer;
import com.firefly.common.eda.event.EventEnvelope;
import com.firefly.common.eda.listener.EventListenerProcessor;
import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.serialization.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka event consumer implementation.
 * <p>
 * This consumer receives events from Apache Kafka topics and emits them
 * as a reactive stream for processing by application event handlers.
 */
@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "firefly.eda.consumer", name = "enabled", havingValue = "true")
@Slf4j
public class KafkaEventConsumer implements EventConsumer {

    private final EdaProperties edaProperties;
    private final EventListenerProcessor eventListenerProcessor;
    private final MessageSerializer messageSerializer;
    private volatile Sinks.Many<EventEnvelope> eventSink = Sinks.many().multicast().onBackpressureBuffer();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    @Autowired(required = false)
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public KafkaEventConsumer(EdaProperties edaProperties, EventListenerProcessor eventListenerProcessor, MessageSerializer messageSerializer) {
        this.edaProperties = edaProperties;
        this.eventListenerProcessor = eventListenerProcessor;
        this.messageSerializer = messageSerializer;
        log.info("Initializing Kafka event consumer (without auto-start)");
    }

    @Override
    public Flux<EventEnvelope> consume() {
        return eventSink.asFlux()
                .doOnSubscribe(subscription -> {
                    if (!running.get()) {
                        start().subscribe();
                    }
                });
    }

    @Override
    public Flux<EventEnvelope> consume(String... destinations) {
        return consume()
                .filter(envelope -> {
                    if (destinations.length == 0) return true;
                    return Arrays.stream(destinations)
                            .anyMatch(dest -> dest.equals(envelope.destination()));
                });
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("🚀 Starting Kafka event consumer");

                // Always recreate the event sink to ensure it's fresh for each start
                eventSink = Sinks.many().multicast().onBackpressureBuffer();
                log.debug("🔄 Recreated event sink for new consumer session");

                // Start all Kafka listener containers
                if (kafkaListenerEndpointRegistry != null) {
                    kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(container -> {
                        if (!container.isRunning()) {
                            log.info("Starting Kafka listener container: {}", container.getListenerId());
                            container.start();
                        }
                    });
                    log.info("✅ Kafka event consumer started successfully");
                } else {
                    log.warn("KafkaListenerEndpointRegistry not available - listeners may not start");
                }
            } else {
                log.debug("Kafka event consumer already running");
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("🛑 Stopping Kafka event consumer");

                // Stop all Kafka listener containers
                if (kafkaListenerEndpointRegistry != null) {
                    kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(container -> {
                        if (container.isRunning()) {
                            log.info("Stopping Kafka listener container: {}", container.getListenerId());
                            container.stop();
                        }
                    });
                }

                eventSink.tryEmitComplete();
                log.info("✅ Kafka event consumer stopped successfully");
            } else {
                log.debug("Kafka event consumer already stopped");
            }
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getConsumerType() {
        return "KAFKA";
    }

    @Override
    public boolean isAvailable() {
        return edaProperties.getConsumer().isEnabled();
    }

    @Override
    public Mono<ConsumerHealth> getHealth() {
        Map<String, Object> details = new HashMap<>();
        details.put("running", isRunning());
        details.put("enabled", edaProperties.getConsumer().isEnabled());
        details.put("groupId", edaProperties.getConsumer().getGroupId());
        
        return Mono.just(ConsumerHealth.builder()
                .consumerType(getConsumerType())
                .available(isAvailable())
                .running(isRunning())
                .status(isAvailable() && isRunning() ? "UP" : "DOWN")
                .details(details)
                .build());
    }

    /**
     * Kafka listener method that receives messages and emits them to the reactive stream.
     * <p>
     * This method is automatically called by Spring Kafka when messages are received.
     * The topics are configured via application properties.
     * Supports regex patterns (e.g., "test-.*" will match "test-topic-1", "test-topic-2", etc.)
     */
    @KafkaListener(
            topicPattern = "${firefly.eda.consumer.kafka.default.topics:events}",
            groupId = "${firefly.eda.consumer.group-id:firefly-eda}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleKafkaMessage(ConsumerRecord<String, String> record) {
        int messageNumber = messageCounter.incrementAndGet();

        String topic = record.topic();
        String payload = record.value();
        String key = record.key();
        int partition = record.partition();
        long timestamp = record.timestamp();
        long offset = record.offset();

        try {
            log.info("📥 [Kafka Consumer] Received message #{}", messageNumber);
            log.debug("Topic: {}, Partition: {}, Offset: {}, Key: {}",
                     topic, partition, offset, key);

            // Extract headers from ConsumerRecord
            Map<String, Object> headers = extractHeaders(record);

            // Get event type from headers or use topic name
            String eventType = getEventType(headers, topic);

            // Create acknowledgment callback (auto-ack with RECORD mode)
            EventEnvelope.AckCallback ackCallback = new KafkaAckCallback(null);

            // Create event envelope using unified EventEnvelope
            EventEnvelope envelope = EventEnvelope.forConsuming(
                    topic,
                    eventType,
                    payload,
                    getTransactionId(headers),
                    headers,
                    EventEnvelope.EventMetadata.empty(), // metadata
                    Instant.ofEpochMilli(timestamp),
                    getConsumerType(),
                    "default", // connectionId
                    ackCallback
            );

            // Emit to reactive stream
            Sinks.EmitResult result = eventSink.tryEmitNext(envelope);
            if (result.isFailure()) {
                log.warn("❌ Failed to emit Kafka event to reactive stream: {}", result);
                // Message will be auto-acknowledged by RECORD mode
            } else {
                log.debug("✅ Message #{} emitted to reactive stream successfully", messageNumber);
            }

            // Deserialize the event using the correct type from headers
            Object deserializedEvent = deserializeEvent(payload, headers);
            log.debug("Deserialized event type: {}", deserializedEvent.getClass().getSimpleName());

            // Process the event through EventListenerProcessor (like RabbitMQ does)
            eventListenerProcessor.processEvent(deserializedEvent, headers)
                    .doOnSuccess(v -> {
                        log.debug("✅ [Kafka Consumer] Successfully processed message #{} through EventListenerProcessor", messageNumber);
                    })
                    .doOnError(error -> {
                        log.error("❌ [Kafka Consumer] Failed to process message #{} through EventListenerProcessor", messageNumber, error);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("❌ Error processing Kafka message #{}: topic={}, partition={}, offset={}, error={}",
                     messageNumber, topic, partition, offset, e.getMessage(), e);

            // Message will be auto-acknowledged by RECORD mode even on error
        }
    }

    private Map<String, Object> extractHeaders(ConsumerRecord<String, String> record) {
        Map<String, Object> headers = new HashMap<>();

        // Add standard headers for EventListenerProcessor topic routing
        headers.put("topic", record.topic());
        headers.put("destination", record.topic());
        
        // Add Kafka-specific headers
        headers.put("kafka.topic", record.topic());
        headers.put("kafka.partition", record.partition());
        headers.put("kafka.offset", record.offset());
        headers.put("kafka.key", record.key());
        headers.put("kafka.timestamp", record.timestamp());
        headers.put("kafka.timestampType", record.timestampType());

        // Extract custom headers
        for (org.apache.kafka.common.header.Header header : record.headers()) {
            String key = header.key();
            byte[] value = header.value();
            if (value != null) {
                headers.put(key, new String(value));
            }
        }

        return headers;
    }

    private Map<String, Object> extractHeadersFromMessage(org.springframework.messaging.Message<String> message) {
        Map<String, Object> headers = new HashMap<>();

        // Extract all headers from Spring Message
        message.getHeaders().forEach((key, value) -> {
            if (value instanceof byte[]) {
                headers.put(key, new String((byte[]) value));
            } else {
                headers.put(key, value);
            }
        });

        return headers;
    }

    private String getEventType(Map<String, Object> headers, String defaultTopic) {
        Object eventType = headers.get("event-type");
        return eventType != null ? eventType.toString() : defaultTopic;
    }

    private String getTransactionId(Map<String, Object> headers) {
        Object transactionId = headers.get("transaction-id");
        return transactionId != null ? transactionId.toString() : null;
    }

    /**
     * Kafka-specific acknowledgment callback implementation.
     */
    private static class KafkaAckCallback implements EventEnvelope.AckCallback {
        private final Acknowledgment acknowledgment;

        public KafkaAckCallback(Acknowledgment acknowledgment) {
            this.acknowledgment = acknowledgment;
        }

        @Override
        public Mono<Void> acknowledge() {
            return Mono.fromRunnable(() -> {
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            });
        }

        @Override
        public Mono<Void> reject(Throwable error) {
            return Mono.fromRunnable(() -> {
                // Kafka doesn't have explicit reject - we just don't acknowledge
                // The message will be redelivered based on Kafka configuration
                log.warn("Rejecting Kafka message (not acknowledging): {}", error.getMessage());
            });
        }
    }

    /**
     * Deserializes the event using class information from headers.
     */
    private Object deserializeEvent(String messageBody, Map<String, Object> headers) {
        // Try to get the event class from headers
        Object eventClassHeader = headers.get("event_class");
        if (eventClassHeader != null) {
            try {
                Class<?> eventClass = Class.forName(eventClassHeader.toString());
                log.debug("Deserializing event as: {}", eventClass.getSimpleName());
                return messageSerializer.deserialize(messageBody, eventClass);
            } catch (ClassNotFoundException e) {
                log.warn("Event class not found: {}, falling back to Object.class", eventClassHeader);
            } catch (Exception e) {
                log.warn("Failed to deserialize event as {}, falling back to Object.class: {}",
                        eventClassHeader, e.getMessage());
            }
        }

        // Fallback to generic deserialization
        try {
            log.debug("No event class information found in headers, deserializing as Object");
            return messageSerializer.deserialize(messageBody, Object.class);
        } catch (Exception e) {
            log.error("Failed to deserialize event as Object: {}", e.getMessage());
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}