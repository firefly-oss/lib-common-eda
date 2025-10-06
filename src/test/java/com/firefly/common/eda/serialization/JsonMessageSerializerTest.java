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

package com.firefly.common.eda.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JsonMessageSerializer.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>Serialization of various object types</li>
 *   <li>Deserialization to target types</li>
 *   <li>Handling of null values</li>
 *   <li>Handling of edge cases</li>
 *   <li>Error handling for invalid data</li>
 * </ul>
 */
class JsonMessageSerializerTest {

    private JsonMessageSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        serializer = new JsonMessageSerializer(objectMapper);
    }

    @Test
    @DisplayName("Should serialize simple object to JSON bytes")
    void shouldSerializeSimpleObjectToJsonBytes() throws SerializationException {
        // Arrange
        TestEventModels.SimpleTestEvent event = TestEventModels.SimpleTestEvent.create("test message");
        
        // Act
        byte[] serialized = serializer.serialize(event);
        
        // Assert
        assertThat(serialized).isNotNull();
        String json = new String(serialized, StandardCharsets.UTF_8);
        assertThat(json).contains("\"message\":\"test message\"");
        assertThat(json).contains("\"id\":");
        assertThat(json).contains("\"timestamp\":");
    }

    @Test
    @DisplayName("Should deserialize JSON bytes to target type")
    void shouldDeserializeJsonBytesToTargetType() throws SerializationException {
        // Arrange
        TestEventModels.SimpleTestEvent original = TestEventModels.SimpleTestEvent.create("test message");
        byte[] serialized = serializer.serialize(original);
        
        // Act
        TestEventModels.SimpleTestEvent deserialized = serializer.deserialize(
                serialized, 
                TestEventModels.SimpleTestEvent.class
        );
        
        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getMessage()).isEqualTo(original.getMessage());
        assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
    }

    @Test
    @DisplayName("Should serialize and deserialize complex nested objects")
    void shouldSerializeAndDeserializeComplexNestedObjects() throws SerializationException {
        // Arrange
        TestEventModels.ComplexEvent original = TestEventModels.ComplexEvent.create();
        
        // Act
        byte[] serialized = serializer.serialize(original);
        TestEventModels.ComplexEvent deserialized = serializer.deserialize(
                serialized, 
                TestEventModels.ComplexEvent.class
        );
        
        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getData()).isNotNull();
        assertThat(deserialized.getData().getField1()).isEqualTo(original.getData().getField1());
        assertThat(deserialized.getData().getField2()).isEqualTo(original.getData().getField2());
        assertThat(deserialized.getData().getInnerData()).isNotNull();
        assertThat(deserialized.getData().getInnerData().getValue())
                .isEqualTo(original.getData().getInnerData().getValue());
        assertThat(deserialized.getData().getInnerData().getFlag())
                .isEqualTo(original.getData().getInnerData().getFlag());
    }

    @Test
    @DisplayName("Should handle null payload")
    void shouldHandleNullPayload() throws SerializationException {
        // Act
        byte[] serialized = serializer.serialize(null);
        
        // Assert
        assertThat(serialized).isNotNull();
        assertThat(new String(serialized, StandardCharsets.UTF_8)).isEqualTo("null");
    }

    @Test
    @DisplayName("Should handle String payload directly")
    void shouldHandleStringPayloadDirectly() throws SerializationException {
        // Arrange
        String payload = "simple string message";
        
        // Act
        byte[] serialized = serializer.serialize(payload);
        
        // Assert
        assertThat(serialized).isNotNull();
        assertThat(new String(serialized, StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    @DisplayName("Should handle byte array payload directly")
    void shouldHandleByteArrayPayloadDirectly() throws SerializationException {
        // Arrange
        byte[] payload = "test bytes".getBytes(StandardCharsets.UTF_8);
        
        // Act
        byte[] serialized = serializer.serialize(payload);
        
        // Assert
        assertThat(serialized).isEqualTo(payload);
    }

    @Test
    @DisplayName("Should deserialize to String type")
    void shouldDeserializeToStringType() throws SerializationException {
        // Arrange
        byte[] data = "test string".getBytes(StandardCharsets.UTF_8);
        
        // Act
        String result = serializer.deserialize(data, String.class);
        
        // Assert
        assertThat(result).isEqualTo("test string");
    }

    @Test
    @DisplayName("Should deserialize to byte array type")
    void shouldDeserializeToByteArrayType() throws SerializationException {
        // Arrange
        byte[] data = "test bytes".getBytes(StandardCharsets.UTF_8);
        
        // Act
        byte[] result = serializer.deserialize(data, byte[].class);
        
        // Assert
        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("Should return null for null or empty data")
    void shouldReturnNullForNullOrEmptyData() throws SerializationException {
        // Act & Assert
        assertThat(serializer.deserialize((byte[])null, String.class)).isNull();
        assertThat(serializer.deserialize(new byte[0], String.class)).isNull();
    }

    @Test
    @DisplayName("Should throw SerializationException for invalid JSON")
    void shouldThrowSerializationExceptionForInvalidJson() {
        // Arrange
        byte[] invalidJson = "{invalid json}".getBytes(StandardCharsets.UTF_8);
        
        // Act & Assert
        assertThatThrownBy(() -> serializer.deserialize(invalidJson, TestEventModels.SimpleTestEvent.class))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to deserialize JSON");
    }

    @Test
    @DisplayName("Should have correct format and content type")
    void shouldHaveCorrectFormatAndContentType() {
        // Assert
        assertThat(serializer.getFormat()).isEqualTo(SerializationFormat.JSON);
        assertThat(serializer.getContentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should have high priority as default serializer")
    void shouldHaveHighPriorityAsDefaultSerializer() {
        // Assert
        assertThat(serializer.getPriority()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should serialize objects with Java Time types")
    void shouldSerializeObjectsWithJavaTimeTypes() throws SerializationException {
        // Arrange
        TestEventModels.OrderCreatedEvent event = new TestEventModels.OrderCreatedEvent(
                "order-123",
                "customer-456",
                99.99,
                "USD",
                Instant.parse("2025-01-01T12:00:00Z")
        );
        
        // Act
        byte[] serialized = serializer.serialize(event);
        TestEventModels.OrderCreatedEvent deserialized = serializer.deserialize(
                serialized, 
                TestEventModels.OrderCreatedEvent.class
        );
        
        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getOrderId()).isEqualTo(event.getOrderId());
        assertThat(deserialized.getCreatedAt()).isEqualTo(event.getCreatedAt());
    }

    @Test
    @DisplayName("Should deserialize from String using convenience method")
    void shouldDeserializeFromStringUsingConvenienceMethod() throws SerializationException {
        // Arrange
        String json = "{\"id\":\"123\",\"message\":\"test\",\"timestamp\":\"2025-01-01T12:00:00Z\"}";
        
        // Act
        TestEventModels.SimpleTestEvent result = serializer.deserialize(
                json, 
                TestEventModels.SimpleTestEvent.class
        );
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("123");
        assertThat(result.getMessage()).isEqualTo("test");
    }
}

