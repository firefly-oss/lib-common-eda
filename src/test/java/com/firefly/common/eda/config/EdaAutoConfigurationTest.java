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
import com.firefly.common.eda.properties.EdaProperties;
import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eda.publisher.SpringApplicationEventPublisher;
import com.firefly.common.eda.serialization.JsonMessageSerializer;
import com.firefly.common.eda.serialization.MessageSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EdaAutoConfiguration.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>Auto-configuration is properly activated</li>
 *   <li>Required beans are created</li>
 *   <li>Conditional bean creation works correctly</li>
 *   <li>Component scanning discovers EDA components</li>
 * </ul>
 */
@SpringBootTest(classes = com.firefly.common.eda.testconfig.TestApplication.class)
@ActiveProfiles("test")
class EdaAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should create FireflyEdaAutoConfiguration bean")
    void shouldCreateFireflyEdaAutoConfigurationBean() {
        // Assert
        assertThat(applicationContext.containsBean("fireflyEdaAutoConfiguration")).isTrue();
        FireflyEdaAutoConfiguration config = applicationContext.getBean(FireflyEdaAutoConfiguration.class);
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("Should create EdaProperties bean")
    void shouldCreateEdaPropertiesBean() {
        // Assert
        assertThat(applicationContext.containsBean("firefly.eda-com.firefly.common.eda.properties.EdaProperties"))
                .isTrue();
        EdaProperties properties = applicationContext.getBean(EdaProperties.class);
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should create default ObjectMapper bean")
    void shouldCreateDefaultObjectMapperBean() {
        // Assert
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        assertThat(objectMapper).isNotNull();
    }

    @Test
    @DisplayName("Should create MessageSerializer bean")
    void shouldCreateMessageSerializerBean() {
        // Assert
        MessageSerializer serializer = applicationContext.getBean(MessageSerializer.class);
        assertThat(serializer).isNotNull();
        assertThat(serializer).isInstanceOf(JsonMessageSerializer.class);
    }

    @Test
    @DisplayName("Should create SpringApplicationEventPublisher bean")
    void shouldCreateSpringApplicationEventPublisherBean() {
        // Assert
        SpringApplicationEventPublisher publisher = applicationContext.getBean(SpringApplicationEventPublisher.class);
        assertThat(publisher).isNotNull();
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should create EventPublisherFactory bean")
    void shouldCreateEventPublisherFactoryBean() {
        // Assert
        EventPublisherFactory factory = applicationContext.getBean(EventPublisherFactory.class);
        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("Should discover all EventPublisher implementations")
    void shouldDiscoverAllEventPublisherImplementations() {
        // Act
        var publishers = applicationContext.getBeansOfType(EventPublisher.class);
        
        // Assert - At minimum, SpringApplicationEventPublisher should be present
        assertThat(publishers).isNotEmpty();
        assertThat(publishers.values())
                .anyMatch(p -> p instanceof SpringApplicationEventPublisher);
    }

    /**
     * Test that auto-configuration is disabled when firefly.eda.enabled=false
     */
    @SpringBootTest(classes = com.firefly.common.eda.testconfig.TestApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "firefly.eda.enabled=false"
    })
    static class DisabledAutoConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("Should not create EdaAutoConfiguration when disabled")
        void shouldNotCreateEdaAutoConfigurationWhenDisabled() {
            // Assert - EdaAutoConfiguration should not be created
            assertThat(applicationContext.containsBean("edaAutoConfiguration")).isFalse();
        }
    }

    /**
     * Test that application event publisher can be disabled
     */
    @SpringBootTest(classes = com.firefly.common.eda.testconfig.TestApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "firefly.eda.publishers.application-event.enabled=false"
    })
    static class DisabledApplicationEventPublisherTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("Should not create SpringApplicationEventPublisher when disabled")
        void shouldNotCreateSpringApplicationEventPublisherWhenDisabled() {
            // Assert - SpringApplicationEventPublisher should not be created
            boolean hasPublisher = applicationContext.getBeansOfType(SpringApplicationEventPublisher.class)
                    .values().stream()
                    .anyMatch(p -> p.isAvailable());
            
            assertThat(hasPublisher).isFalse();
        }
    }
}

