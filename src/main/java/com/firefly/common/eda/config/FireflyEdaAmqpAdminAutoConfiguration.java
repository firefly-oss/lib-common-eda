package com.firefly.common.eda.config;

import com.firefly.common.eda.properties.EdaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Firefly EDA AMQP Admin Auto-Configuration.
 *
 * <p>This configuration creates RabbitMQ infrastructure management beans
 * using ONLY firefly.eda.publishers.rabbitmq.* properties.
 *
 * <p><strong>IMPORTANT - Hexagonal Architecture:</strong>
 * This auto-configuration reads configuration EXCLUSIVELY from firefly.eda.publishers.rabbitmq.* namespace.
 * It does NOT use spring.rabbitmq.* or spring.amqp.* properties.
 *
 * <p>Beans created when not already defined:
 * <ul>
 *   <li>RabbitMQ ConnectionFactory from Firefly EDA properties</li>
 *   <li>AmqpAdmin for RabbitMQ infrastructure management</li>
 * </ul>
 */
@AutoConfiguration(after = {FireflyEdaAutoConfiguration.class, FireflyEdaRabbitMqAutoConfiguration.class})
@ConditionalOnClass({AmqpAdmin.class})
@ConditionalOnProperty(prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EdaProperties.class)
@Slf4j
public class FireflyEdaAmqpAdminAutoConfiguration {

    public FireflyEdaAmqpAdminAutoConfiguration(EdaProperties props) {
        // Only log if publishers are enabled and RabbitMQ is configured
        if (props.getPublishers().isEnabled()) {
            var rabbitPublisher = props.getPublishers().getRabbitmq().get("default");
            if (rabbitPublisher != null && rabbitPublisher.isEnabled() &&
                rabbitPublisher.getHost() != null && !rabbitPublisher.getHost().isEmpty()) {
                log.info("--------------------------------------------------------------------------------");
                log.info("FIREFLY EDA AMQP ADMIN - INITIALIZING");
                log.info("--------------------------------------------------------------------------------");
            } else {
                log.debug("Firefly EDA AMQP Admin auto-configuration loaded but not creating beans (disabled or not configured)");
            }
        } else {
            log.debug("Firefly EDA AMQP Admin auto-configuration loaded but not creating beans (publishers globally disabled)");
        }
    }

    /**
     * Creates a RabbitAdmin for managing RabbitMQ infrastructure when:
     * - RabbitMQ classes are available on classpath
     * - No existing AmqpAdmin bean with this name exists
     * - Firefly EDA ConnectionFactory is available
     *
     * <p><strong>Uses:</strong> fireflyEdaRabbitPublisherConnectionFactory bean (shared with publisher)
     */
    @Bean(name = "fireflyEdaAmqpAdmin")
    @ConditionalOnMissingBean(name = "fireflyEdaAmqpAdmin")
    @ConditionalOnBean(name = "fireflyEdaRabbitPublisherConnectionFactory")
    public AmqpAdmin fireflyEdaAmqpAdmin(
            @org.springframework.beans.factory.annotation.Qualifier("fireflyEdaRabbitPublisherConnectionFactory")
            org.springframework.amqp.rabbit.connection.ConnectionFactory fireflyEdaRabbitPublisherConnectionFactory) {
        log.info("Creating RabbitAdmin from fireflyEdaRabbitPublisherConnectionFactory");
        RabbitAdmin admin = new RabbitAdmin(fireflyEdaRabbitPublisherConnectionFactory);
        log.info("AMQP Admin infrastructure created successfully");
        log.info("--------------------------------------------------------------------------------");
        return admin;
    }
}