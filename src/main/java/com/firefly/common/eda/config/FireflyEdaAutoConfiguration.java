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

import com.firefly.common.eda.properties.EdaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main auto-configuration class for Firefly EDA library.
 * <p>
 * This class provides a general overview of the EDA configuration and delegates
 * to specific auto-configuration classes for each provider (Kafka, RabbitMQ, etc.).
 * <p>
 * <strong>Configuration Namespace:</strong> firefly.eda.*
 * <p>
 * <strong>Key Properties:</strong>
 * <ul>
 *   <li>firefly.eda.enabled - Enable/disable the entire EDA library (default: true)</li>
 *   <li>firefly.eda.publishers.enabled - Enable/disable all publishers (default: true)</li>
 *   <li>firefly.eda.consumer.enabled - Enable/disable all consumers (default: true)</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EdaProperties.class)
public class FireflyEdaAutoConfiguration {

    public FireflyEdaAutoConfiguration(EdaProperties props) {
        log.info("╔════════════════════════════════════════════════════════════════════════════════╗");
        log.info("║  🚀 FIREFLY EDA - EVENT-DRIVEN ARCHITECTURE LIBRARY                           ║");
        log.info("╚════════════════════════════════════════════════════════════════════════════════╝");
        log.info("");
        log.info("📋 Global Configuration Summary:");
        log.info("   • EDA Library: {}", props.isEnabled() ? "✅ ENABLED" : "❌ DISABLED");
        log.info("   • Publishers: {}", props.getPublishers().isEnabled() ? "✅ ENABLED" : "❌ DISABLED");
        log.info("   • Consumers: {}", props.getConsumer().isEnabled() ? "✅ ENABLED" : "❌ DISABLED");
        log.info("   • Default Publisher Type: {}", props.getDefaultPublisherType());
        log.info("   • Default Serialization: {}", props.getDefaultSerializationFormat());
        log.info("   • Consumer Group ID: {}", props.getConsumer().getGroupId());
        log.info("   • Metrics: {}", props.isMetricsEnabled() ? "✅ ENABLED" : "❌ DISABLED");
        log.info("   • Health Checks: {}", props.isHealthEnabled() ? "✅ ENABLED" : "❌ DISABLED");
        log.info("   • Tracing: {}", props.isTracingEnabled() ? "✅ ENABLED" : "❌ DISABLED");
        log.info("");
        
        // Log publisher details
        if (props.getPublishers().isEnabled()) {
            log.info("📤 Publishers Configuration:");
            
            // Kafka Publisher
            var kafkaPublisher = props.getPublishers().getKafka().get("default");
            if (kafkaPublisher != null && kafkaPublisher.isEnabled()) {
                String bootstrap = kafkaPublisher.getBootstrapServers();
                log.info("   • Kafka Publisher: {} (bootstrap: {})", 
                    bootstrap != null && !bootstrap.isEmpty() ? "✅ CONFIGURED" : "⚠️  NOT CONFIGURED",
                    bootstrap != null && !bootstrap.isEmpty() ? bootstrap : "NOT SET");
            } else {
                log.info("   • Kafka Publisher: ❌ DISABLED");
            }
            
            // RabbitMQ Publisher
            var rabbitPublisher = props.getPublishers().getRabbitmq().get("default");
            if (rabbitPublisher != null && rabbitPublisher.isEnabled()) {
                log.info("   • RabbitMQ Publisher: ✅ CONFIGURED (host: {}:{})", 
                    rabbitPublisher.getHost(), rabbitPublisher.getPort());
            } else {
                log.info("   • RabbitMQ Publisher: ❌ DISABLED");
            }
            
            // Application Event Publisher
            if (props.getPublishers().getApplicationEvent().isEnabled()) {
                log.info("   • Application Event Publisher: ✅ ENABLED");
            } else {
                log.info("   • Application Event Publisher: ❌ DISABLED");
            }
        } else {
            log.info("📤 Publishers: ❌ GLOBALLY DISABLED");
        }
        
        log.info("");
        
        // Log consumer details
        if (props.getConsumer().isEnabled()) {
            log.info("📥 Consumers Configuration:");
            
            // Kafka Consumer
            var kafkaConsumer = props.getConsumer().getKafka().get("default");
            if (kafkaConsumer != null && kafkaConsumer.isEnabled()) {
                String bootstrap = kafkaConsumer.getBootstrapServers();
                log.info("   • Kafka Consumer: {} (bootstrap: {})", 
                    bootstrap != null && !bootstrap.isEmpty() ? "✅ CONFIGURED" : "⚠️  NOT CONFIGURED",
                    bootstrap != null && !bootstrap.isEmpty() ? bootstrap : "NOT SET");
            } else {
                log.info("   • Kafka Consumer: ❌ DISABLED");
            }
            
            // RabbitMQ Consumer
            var rabbitConsumer = props.getConsumer().getRabbitmq().get("default");
            if (rabbitConsumer != null && rabbitConsumer.isEnabled()) {
                log.info("   • RabbitMQ Consumer: ✅ CONFIGURED (host: {}:{})", 
                    rabbitConsumer.getHost(), rabbitConsumer.getPort());
            } else {
                log.info("   • RabbitMQ Consumer: ❌ DISABLED");
            }
            
            // Application Event Consumer
            if (props.getConsumer().getApplicationEvent().isEnabled()) {
                log.info("   • Application Event Consumer: ✅ ENABLED");
            } else {
                log.info("   • Application Event Consumer: ❌ DISABLED");
            }
        } else {
            log.info("📥 Consumers: ❌ GLOBALLY DISABLED");
        }
        
        log.info("");
        log.info("🔍 Specific provider beans will be created based on above configuration");
        log.info("════════════════════════════════════════════════════════════════════════════════");
    }
}

