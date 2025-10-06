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

package com.firefly.common.eda.publisher.rabbitmq;

import com.firefly.common.eda.annotation.PublisherType;
import com.firefly.common.eda.publisher.ConnectionAwarePublisher;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.PublisherHealth;
import com.firefly.common.eda.serialization.MessageSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ implementation of EventPublisher using Spring AMQP.
 * <p>
 * This publisher provides:
 * <ul>
 *   <li>Async publishing using reactor</li>
 *   <li>Proper header and message property mapping</li>
 *   <li>Connection health monitoring</li>
 *   <li>Exchange and routing key support</li>
 * </ul>
 */
@Component
@ConditionalOnClass({AmqpTemplate.class, ConnectionFactory.class})
@ConditionalOnBean(AmqpTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class RabbitMqEventPublisher implements EventPublisher, ConnectionAwarePublisher {

    private final AmqpTemplate amqpTemplate;
    private final ConnectionFactory connectionFactory;
    private final MessageSerializer messageSerializer;
    private String connectionId;

    @Override
    public PublisherType getPublisherType() {
        return PublisherType.RABBITMQ;
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("📤 [RabbitMQ Publisher] Publishing event to destination: {}", destination);
                log.debug("Event type: {}, Headers: {}", event.getClass().getSimpleName(), headers);

                // Serialize the event
                byte[] serializedEvent = messageSerializer.serialize(event);
                log.debug("Serialized event size: {} bytes", serializedEvent.length);

                // Convert destination to exchange and routing key
                // Format: "exchange/routingKey" or just "exchange" (empty routing key)
                String[] parts = destination.split("/", 2);
                String exchange = parts[0];
                String routingKey = parts.length > 1 ? parts[1] : "";

                log.info("Exchange: '{}', Routing Key: '{}'", exchange, routingKey);

                // Create message properties and set headers
                MessageProperties messageProperties = new MessageProperties();
                messageProperties.setContentType("application/json");
                messageProperties.setTimestamp(java.util.Date.from(Instant.now()));

                if (headers != null && !headers.isEmpty()) {
                    Map<String, Object> amqpHeaders = new HashMap<>(headers);
                    messageProperties.setHeaders(amqpHeaders);
                    log.debug("Added {} custom headers", headers.size());
                }

                // Set additional metadata
                messageProperties.setHeader("publisher_type", getPublisherType().name());
                messageProperties.setHeader("connection_id", getConnectionId());
                messageProperties.setHeader("event_class", event.getClass().getName());

                // Create the message
                Message message = new Message(serializedEvent, messageProperties);

                // Publish the message
                amqpTemplate.send(exchange, routingKey, message);

                log.info("✅ [RabbitMQ Publisher] Successfully published event to exchange: '{}', routing key: '{}'", exchange, routingKey);

            } catch (Exception e) {
                log.error("Failed to publish event to RabbitMQ destination: {}", destination, e);
                throw new RuntimeException("Failed to publish event to RabbitMQ", e);
            }
        })
        .then()
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<PublisherHealth> getHealth() {
        return Mono.fromCallable(() -> {
            PublisherHealth.PublisherHealthBuilder healthBuilder = PublisherHealth.builder()
                    .publisherType(getPublisherType())
                    .connectionId(getConnectionId())
                    .lastChecked(Instant.now());

            try {
                // Test connection by creating and closing a connection
                Connection connection = connectionFactory.createConnection();
                boolean isOpen = connection.isOpen();
                connection.close();

                if (isOpen) {
                    return healthBuilder
                            .status("UP")
                            .available(true)
                            .details(Map.of(
                                    "connection_factory", connectionFactory.getClass().getSimpleName(),
                                    "amqp_template", amqpTemplate.getClass().getSimpleName()
                            ))
                            .build();
                } else {
                    return healthBuilder
                            .status("DOWN")
                            .available(false)
                            .errorMessage("RabbitMQ connection is not open")
                            .build();
                }

            } catch (Exception e) {
                log.warn("RabbitMQ health check failed for connection: {}", getConnectionId(), e);
                return healthBuilder
                        .status("DOWN")
                        .available(false)
                        .errorMessage("RabbitMQ connection failed: " + e.getMessage())
                        .details(Map.of("error_type", e.getClass().getSimpleName()))
                        .build();
            }
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public boolean isAvailable() {
        try {
            Connection connection = connectionFactory.createConnection();
            boolean isOpen = connection.isOpen();
            connection.close();
            return isOpen;
        } catch (Exception e) {
            log.debug("RabbitMQ availability check failed: {}", e.getMessage());
            return false;
        }
    }
}