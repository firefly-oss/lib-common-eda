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

import com.firefly.common.eda.testconfig.TestEventModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AvroMessageSerializer.
 * <p>
 * These tests are only enabled when Avro is available on the classpath.
 * Tests verify:
 * <ul>
 *   <li>Serialization of POJOs using reflection</li>
 *   <li>Deserialization to target types</li>
 *   <li>Handling of null values</li>
 *   <li>Handling of byte arrays</li>
 *   <li>Error handling for invalid data</li>
 * </ul>
 */
@EnabledIfSystemProperty(named = "test.serialization.avro", matches = "true", disabledReason = "Avro tests disabled by default")
@DisplayName("AvroMessageSerializer Tests")
class AvroMessageSerializerTest {

    private AvroMessageSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new AvroMessageSerializer();
    }

    @Test
    @DisplayName("Should serialize simple POJO using reflection")
    void shouldSerializeSimplePojoUsingReflection() throws SerializationException {
        // Arrange
        SimpleTestObject event = new SimpleTestObject("test-123", "test message", 42);

        // Act
        byte[] serialized = serializer.serialize(event);

        // Assert
        assertThat(serialized).isNotNull();
        assertThat(serialized.length).isGreaterThan(0);
        // Avro binary format should be more compact than JSON
        assertThat(serialized.length).isLessThan(200);
    }

    @Test
    @DisplayName("Should deserialize binary data to target POJO type")
    void shouldDeserializeBinaryDataToTargetPojoType() throws SerializationException {
        // Arrange
        SimpleTestObject original = new SimpleTestObject("test-123", "test message", 42);
        byte[] serialized = serializer.serialize(original);

        // Act
        SimpleTestObject deserialized = serializer.deserialize(
                serialized,
                SimpleTestObject.class
        );

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getMessage()).isEqualTo(original.getMessage());
        assertThat(deserialized.getNumber()).isEqualTo(original.getNumber());
    }

    @Test
    @DisplayName("Should serialize and deserialize simple objects without Java Time types")
    void shouldSerializeAndDeserializeSimpleObjectsWithoutJavaTimeTypes() throws SerializationException {
        // Arrange - Use a simple object without Instant
        SimpleTestObject original = new SimpleTestObject("test-id", "test message", 42);

        // Act
        byte[] serialized = serializer.serialize(original);
        SimpleTestObject deserialized = serializer.deserialize(
                serialized,
                SimpleTestObject.class
        );

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getMessage()).isEqualTo(original.getMessage());
        assertThat(deserialized.getNumber()).isEqualTo(original.getNumber());
    }

    // Simple test class that works well with Avro reflection
    public static class SimpleTestObject {
        private String id;
        private String message;
        private int number;

        public SimpleTestObject() {} // Required for Avro

        public SimpleTestObject(String id, String message, int number) {
            this.id = id;
            this.message = message;
            this.number = number;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
    }

    @Test
    @DisplayName("Should handle null payload")
    void shouldHandleNullPayload() throws SerializationException {
        // Act
        byte[] serialized = serializer.serialize(null);
        
        // Assert
        assertThat(serialized).isNotNull();
        assertThat(serialized).isEmpty();
    }

    @Test
    @DisplayName("Should handle byte array payload directly")
    void shouldHandleByteArrayPayloadDirectly() throws SerializationException {
        // Arrange
        byte[] payload = "test bytes".getBytes();
        
        // Act
        byte[] serialized = serializer.serialize(payload);
        
        // Assert
        assertThat(serialized).isEqualTo(payload);
    }

    @Test
    @DisplayName("Should deserialize to byte array type")
    void shouldDeserializeToByteArrayType() throws SerializationException {
        // Arrange
        byte[] data = "test data".getBytes();
        
        // Act
        byte[] result = serializer.deserialize(data, byte[].class);
        
        // Assert
        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("Should handle empty byte array")
    void shouldHandleEmptyByteArray() throws SerializationException {
        // Arrange
        byte[] emptyData = new byte[0];
        
        // Act
        TestEventModels.SimpleTestEvent result = serializer.deserialize(
                emptyData, 
                TestEventModels.SimpleTestEvent.class
        );
        
        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should have correct format and content type")
    void shouldHaveCorrectFormatAndContentType() {
        // Assert
        assertThat(serializer.getFormat()).isEqualTo(SerializationFormat.AVRO);
        assertThat(serializer.getContentType()).isEqualTo("application/avro");
    }

    @Test
    @DisplayName("Should have high priority for binary efficiency")
    void shouldHaveHighPriorityForBinaryEfficiency() {
        // Assert
        assertThat(serializer.getPriority()).isEqualTo(80);
    }

    @Test
    @DisplayName("Should indicate it can serialize simple POJOs but not complex types")
    void shouldIndicateItCanSerializeSimplePojoButNotComplexTypes() {
        // Assert - Can serialize simple POJOs
        assertThat(serializer.canSerialize(SimpleTestObject.class)).isTrue();
        assertThat(serializer.canSerialize(String.class)).isTrue();

        // Should not serialize primitives, Java Time types, interfaces, enums
        assertThat(serializer.canSerialize(int.class)).isFalse();
        assertThat(serializer.canSerialize(boolean.class)).isFalse();
        assertThat(serializer.canSerialize(Instant.class)).isFalse(); // Java Time types
        assertThat(serializer.canSerialize(Runnable.class)).isFalse(); // Interfaces
    }

    @Test
    @DisplayName("Should throw SerializationException for invalid binary data")
    void shouldThrowSerializationExceptionForInvalidBinaryData() {
        // Arrange
        byte[] invalidData = "invalid avro data".getBytes();

        // Act & Assert
        assertThatThrownBy(() -> serializer.deserialize(invalidData, SimpleTestObject.class))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to deserialize Avro data");
    }

    @Test
    @DisplayName("Should produce more compact output than JSON for simple objects")
    void shouldProduceMoreCompactOutputThanJsonForSimpleObjects() throws SerializationException {
        // Arrange
        SimpleTestObject testObject = new SimpleTestObject("test-123", "This is a test message for comparison", 42);

        // Configure ObjectMapper with JavaTimeModule for proper JSON serialization
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        JsonMessageSerializer jsonSerializer = new JsonMessageSerializer(objectMapper);

        // Act
        byte[] avroSerialized = serializer.serialize(testObject);
        byte[] jsonSerialized = jsonSerializer.serialize(testObject);

        // Assert
        assertThat(avroSerialized.length).isLessThan(jsonSerialized.length);
    }
}
