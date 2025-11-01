package com.firefly.common.eda.config;

import com.firefly.common.eda.properties.EdaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Firefly EDA RabbitMQ Publisher Auto-Configuration.
 *
 * <p>This configuration creates RabbitMQ infrastructure beans for publishers
 * using ONLY firefly.eda.publishers.rabbitmq.* properties.
 *
 * <p><strong>IMPORTANT - Hexagonal Architecture:</strong>
 * This auto-configuration reads configuration EXCLUSIVELY from firefly.eda.publishers.rabbitmq.* namespace.
 * It does NOT use spring.rabbitmq.* or spring.amqp.* properties.
 *
 * <p>Beans created when not already defined:
 * <ul>
 *   <li>RabbitMQ ConnectionFactory from Firefly EDA properties</li>
 *   <li>RabbitTemplate for message publishing</li>
 * </ul>
 */
@AutoConfiguration(after = FireflyEdaAutoConfiguration.class)
@ConditionalOnClass({RabbitTemplate.class})
@ConditionalOnBean(EdaProperties.class)
@Slf4j
public class FireflyEdaRabbitMqAutoConfiguration {

    public FireflyEdaRabbitMqAutoConfiguration(EdaProperties props) {
        // Only log if publishers are enabled and RabbitMQ is configured
        if (props.getPublishers().isEnabled()) {
            var rabbitPublisher = props.getPublishers().getRabbitmq().get("default");
            if (rabbitPublisher != null && rabbitPublisher.isEnabled() &&
                rabbitPublisher.getHost() != null && !rabbitPublisher.getHost().isEmpty()) {
                log.info("--------------------------------------------------------------------------------");
                log.info("FIREFLY EDA RABBITMQ PUBLISHER - INITIALIZING");
                log.info("--------------------------------------------------------------------------------");
            } else {
                log.debug("Firefly EDA RabbitMQ Publisher auto-configuration loaded but not creating beans (disabled or not configured)");
            }
        } else {
            log.debug("Firefly EDA RabbitMQ Publisher auto-configuration loaded but not creating beans (publishers globally disabled)");
        }
    }

    /**
     * Creates a RabbitMQ ConnectionFactory for publishers from Firefly EDA properties when:
     * - RabbitMQ classes are available on classpath
     * - No existing ConnectionFactory bean with this name exists
     * - RabbitMQ publisher is enabled (defaults to true)
     * - Host is configured in firefly.eda.publishers.rabbitmq.default.host
     *
     * <p><strong>Configuration Source:</strong> firefly.eda.publishers.rabbitmq.default.*
     * <p><strong>NOT using:</strong> spring.rabbitmq.* properties (those are IGNORED)
     */
    @Bean(name = "fireflyEdaRabbitPublisherConnectionFactory")
    @ConditionalOnMissingBean(name = "fireflyEdaRabbitPublisherConnectionFactory")
    @ConditionalOnExpression("${firefly.eda.publishers.enabled:false} && ${firefly.eda.publishers.rabbitmq.default.enabled:true} && '${firefly.eda.publishers.rabbitmq.default.host:}'.length() > 0")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory fireflyEdaRabbitPublisherConnectionFactory(EdaProperties props) {
        log.info("Creating RabbitMQ ConnectionFactory for publishers from firefly.eda.publishers.rabbitmq.default.* properties");
        EdaProperties.Publishers.RabbitMqConfig rabbitProps = props.getPublishers().getRabbitmq().get("default");

        CachingConnectionFactory factory = new CachingConnectionFactory();

        // Configure connection properties from Firefly configuration
        factory.setHost(rabbitProps.getHost());
        factory.setPort(rabbitProps.getPort());
        log.info("  - Host: {}:{}", rabbitProps.getHost(), rabbitProps.getPort());

        factory.setUsername(rabbitProps.getUsername());
        log.info("  - Username: {}", rabbitProps.getUsername());

        factory.setPassword(rabbitProps.getPassword());
        factory.setVirtualHost(rabbitProps.getVirtualHost());
        log.info("  - Virtual host: {}", rabbitProps.getVirtualHost());

        // Test connection
        try {
            factory.createConnection().close();
            log.info("RabbitMQ ConnectionFactory for publishers created and connection tested successfully");
        } catch (Exception e) {
            log.error("Failed to establish RabbitMQ connection for publishers: {}", e.getMessage());
            log.error("Please verify RabbitMQ is running and configuration is correct");
        }

        return factory;
    }

    /**
     * Creates a RabbitTemplate from Firefly-created ConnectionFactory when:
     * - RabbitMQ classes are available on classpath
     * - No existing RabbitTemplate bean with this name exists
     * - Firefly EDA ConnectionFactory is available
     *
     * <p><strong>Uses:</strong> fireflyEdaRabbitPublisherConnectionFactory bean
     */
    @Bean(name = "fireflyEdaRabbitTemplate")
    @ConditionalOnMissingBean(name = "fireflyEdaRabbitTemplate")
    @ConditionalOnBean(name = "fireflyEdaRabbitPublisherConnectionFactory")
    public RabbitTemplate fireflyEdaRabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory fireflyEdaRabbitPublisherConnectionFactory) {
        log.info("Creating RabbitTemplate from fireflyEdaRabbitPublisherConnectionFactory");
        RabbitTemplate template = new RabbitTemplate(fireflyEdaRabbitPublisherConnectionFactory);
        log.info("RabbitMQ Publisher infrastructure created successfully");
        log.info("--------------------------------------------------------------------------------");
        return template;
    }
}