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

package com.firefly.common.eda.testconfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers configuration for integration tests.
 * <p>
 * This configuration provides container instances for:
 * <ul>
 *   <li>Apache Kafka</li>
 *   <li>RabbitMQ</li>
 * </ul>
 * <p>
 * Containers are configured to be reusable across tests for performance.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    /**
     * Kafka container for integration tests.
     * Uses Confluent Platform image with Kafka.
     */
    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        KafkaContainer kafka = new KafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
        );
        kafka.withReuse(true);
        return kafka;
    }

    /**
     * RabbitMQ container for integration tests.
     * Uses official RabbitMQ image with management plugin.
     */
    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitMQContainer() {
        RabbitMQContainer rabbitmq = new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:3.12-management-alpine")
        );
        rabbitmq.withReuse(true);
        return rabbitmq;
    }

}

