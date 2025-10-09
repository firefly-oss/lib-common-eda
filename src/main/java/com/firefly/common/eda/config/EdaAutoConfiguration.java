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

package com.firefly.common.eda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firefly.common.eda.properties.EdaProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for the Event-Driven Architecture library.
 * <p>
 * This configuration class automatically sets up the EDA library when included
 * in a Spring Boot application. It enables configuration properties, sets up
 * component scanning, and provides default beans where needed.
 * <p>
 * Components automatically discovered and configured:
 * <ul>
 *   <li>Event publishers (Kafka, RabbitMQ, Spring Application Events, NOOP)</li>
 *   <li>Event consumers (with filtering and listener processing)</li>
 *   <li>Resilience features (circuit breaker, retry, rate limiting)</li>
 *   <li>Health indicators for Spring Boot Actuator</li>
 *   <li>Metrics collection via Micrometer</li>
 *   <li>Message serialization and filtering</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EdaProperties.class)
@ComponentScan(basePackages = "com.firefly.common.eda")
@EnableAsync
@Slf4j
public class EdaAutoConfiguration {

    public EdaAutoConfiguration() {
        log.info("Firefly EDA Auto-Configuration - Starting initialization");
        log.info("EDA components will be auto-discovered: publishers, consumers, health, metrics, resilience");
    }

    /**
     * Provides a default ObjectMapper for JSON serialization if none exists.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ================================
    // Infrastructure Bean Creation
    // ================================
    // Following the same pattern as lib-common-domain

    /**
     * Creates a Kafka ProducerFactory from Firefly EDA properties when:
     * - Kafka classes are available on classpath
     * - No existing ProducerFactory bean exists
     * - Bootstrap servers are configured in Firefly EDA properties
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnMissingBean(name = "kafkaProducerFactory")
    @ConditionalOnProperty(prefix = "firefly.eda.publishers.kafka.default", name = "bootstrap-servers")
    public ProducerFactory<String, Object> kafkaProducerFactory(EdaProperties props) {
        log.debug("Creating Kafka ProducerFactory from Firefly EDA properties");
        EdaProperties.Publishers.KafkaConfig kafkaProps = props.getPublishers().getKafka().get("default");
        
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.getBootstrapServers());
        log.info("   • Bootstrap servers: {}", kafkaProps.getBootstrapServers());
        
        // Set serializers
        String keySerializer = kafkaProps.getKeySerializer() != null ? 
            kafkaProps.getKeySerializer() : StringSerializer.class.getName();
        String valueSerializer = kafkaProps.getValueSerializer() != null ? 
            kafkaProps.getValueSerializer() : StringSerializer.class.getName();
            
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        
        // Add any additional properties
        if (kafkaProps.getProperties() != null && !kafkaProps.getProperties().isEmpty()) {
            configProps.putAll(kafkaProps.getProperties());
        }
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates a KafkaTemplate from Firefly-created ProducerFactory when:
     * - Kafka classes are available on classpath
     * - No existing KafkaTemplate bean exists
     * - ProducerFactory is available (either user-provided or Firefly-created)
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnMissingBean(name = "kafkaTemplate")
    @ConditionalOnBean(ProducerFactory.class)
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        log.debug("Creating KafkaTemplate from ProducerFactory");
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Creates a Kafka ConsumerFactory from Firefly EDA properties when:
     * - Kafka classes are available on classpath
     * - No existing ConsumerFactory bean exists
     * - Bootstrap servers are configured in Firefly EDA properties
     * - Consumer is enabled
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.core.ConsumerFactory")
    @ConditionalOnMissingBean(name = "kafkaConsumerFactory")
    @ConditionalOnProperty(prefix = "firefly.eda.consumer.kafka.default", name = "bootstrap-servers")
    public ConsumerFactory<String, Object> kafkaConsumerFactory(EdaProperties props) {
        log.debug("Creating Kafka ConsumerFactory from Firefly EDA properties");
        EdaProperties.Consumer.KafkaConfig kafkaProps = props.getConsumer().getKafka().get("default");
        
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.getBootstrapServers());
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, props.getConsumer().getGroupId());
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProps.getAutoOffsetReset() != null ? 
            kafkaProps.getAutoOffsetReset() : "earliest");
        
        // Set deserializers
        String keyDeserializer = kafkaProps.getKeyDeserializer() != null ? 
            kafkaProps.getKeyDeserializer() : StringDeserializer.class.getName();
        String valueDeserializer = kafkaProps.getValueDeserializer() != null ? 
            kafkaProps.getValueDeserializer() : StringDeserializer.class.getName();
            
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        
        // Add any additional properties
        if (kafkaProps.getProperties() != null && !kafkaProps.getProperties().isEmpty()) {
            configProps.putAll(kafkaProps.getProperties());
        }
        
        log.info("   • Bootstrap servers: {}", kafkaProps.getBootstrapServers());
        log.info("   • Group ID: {}", props.getConsumer().getGroupId());
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Creates a Kafka listener container factory from Firefly-created ConsumerFactory when:
     * - Kafka classes are available on classpath
     * - No existing kafkaListenerContainerFactory bean exists
     * - ConsumerFactory is available (either user-provided or Firefly-created)
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
    @ConditionalOnBean(ConsumerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        log.debug("Creating Kafka listener container factory from ConsumerFactory");
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Enable automatic acknowledgment after successful processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }


}