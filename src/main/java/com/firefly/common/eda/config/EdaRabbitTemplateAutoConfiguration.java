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
 * Auto-configuration for RabbitTemplate infrastructure.
 * 
 * <p>This configuration is activated when RabbitTemplate is available on the classpath
 * and provides the following beans when not already defined:
 * <ul>
 *   <li>RabbitMQ ConnectionFactory from Firefly EDA properties</li>
 *   <li>RabbitTemplate for message publishing</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({RabbitTemplate.class})
@EnableConfigurationProperties(EdaProperties.class)
@Slf4j
public class EdaRabbitTemplateAutoConfiguration {

    public EdaRabbitTemplateAutoConfiguration() {
        log.info("Firefly EDA RabbitTemplate Auto-Configuration - Starting initialization");
    }

    /**
     * Creates a RabbitMQ ConnectionFactory from Firefly EDA properties when:
     * - RabbitMQ classes are available on classpath
     * - No existing ConnectionFactory bean exists
     * - RabbitMQ publisher is enabled (defaults to true)
     * - Host is configured in Firefly EDA properties
     */
    @Bean
    @ConditionalOnMissingBean(ConnectionFactory.class)
    @ConditionalOnExpression("${firefly.eda.publishers.rabbitmq.default.enabled:true} && '${firefly.eda.publishers.rabbitmq.default.host:}'.length() > 0")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory(EdaProperties props) {
        log.debug("Creating RabbitMQ ConnectionFactory from Firefly EDA properties");
        EdaProperties.Publishers.RabbitMqConfig rabbitProps = props.getPublishers().getRabbitmq().get("default");

        CachingConnectionFactory factory = new CachingConnectionFactory();

        // Configure connection properties from Firefly configuration
        factory.setHost(rabbitProps.getHost());
        factory.setPort(rabbitProps.getPort());
        log.info("   • Host: {}:{}", rabbitProps.getHost(), rabbitProps.getPort());
        factory.setUsername(rabbitProps.getUsername());
        factory.setPassword(rabbitProps.getPassword());
        factory.setVirtualHost(rabbitProps.getVirtualHost());

        return factory;
    }

    /**
     * Creates a RabbitTemplate from Firefly-created ConnectionFactory when:
     * - RabbitMQ classes are available on classpath
     * - No existing RabbitTemplate bean exists
     * - ConnectionFactory is available (either user-provided or Firefly-created)
     */
    @Bean
    @ConditionalOnMissingBean(name = "rabbitTemplate")
    @ConditionalOnBean(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        log.debug("Creating RabbitTemplate from ConnectionFactory");
        return new RabbitTemplate(connectionFactory);
    }
}