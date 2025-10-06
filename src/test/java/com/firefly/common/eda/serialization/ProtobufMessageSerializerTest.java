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
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProtobufMessageSerializer.
 * <p>
 * These tests are only enabled when Protocol Buffers is available on the classpath.
 * Tests verify:
 * <ul>
 *   <li>Serialization of Protocol Buffer Message objects</li>
 *   <li>Deserialization to target Message types</li>
 *   <li>Handling of null values</li>
 *   <li>Handling of byte arrays</li>
 *   <li>Error handling for invalid data and non-Message types</li>
 * </ul>
 */
@EnabledIfSystemProperty(named = "test.serialization.protobuf", matches = "true", disabledReason = "Protobuf tests disabled by default")
@DisplayName("ProtobufMessageSerializer Tests")
class ProtobufMessageSerializerTest {

    private ProtobufMessageSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new ProtobufMessageSerializer();
    }

    @Test
    @DisplayName("Should serialize Protocol Buffer Message")
    void shouldSerializeProtocolBufferMessage() throws SerializationException {
        // Arrange
        StringValue message = StringValue.of("test message");
        
        // Act
        byte[] serialized = serializer.serialize(message);
        
        // Assert
        assertThat(serialized).isNotNull();
        assertThat(serialized.length).isGreaterThan(0);
        // Protocol Buffer binary format should be compact
        assertThat(serialized.length).isLessThan(50);
    }

    @Test
    @DisplayName("Should deserialize binary data to Protocol Buffer Message")
    void shouldDeserializeBinaryDataToProtocolBufferMessage() throws SerializationException {
        // Arrange
        StringValue original = StringValue.of("test message");
        byte[] serialized = serializer.serialize(original);
        
        // Act
        StringValue deserialized = serializer.deserialize(serialized, StringValue.class);
        
        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getValue()).isEqualTo(original.getValue());
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
        StringValue result = serializer.deserialize(emptyData, StringValue.class);
        
        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should have correct format and content type")
    void shouldHaveCorrectFormatAndContentType() {
        // Assert
        assertThat(serializer.getFormat()).isEqualTo(SerializationFormat.PROTOBUF);
        assertThat(serializer.getContentType()).isEqualTo("application/x-protobuf");
    }

    @Test
    @DisplayName("Should have highest priority for binary efficiency")
    void shouldHaveHighestPriorityForBinaryEfficiency() {
        // Assert
        assertThat(serializer.getPriority()).isEqualTo(90);
    }

    @Test
    @DisplayName("Should indicate it can only serialize Protocol Buffer Messages")
    void shouldIndicateItCanOnlySerializeProtocolBufferMessages() {
        // Assert
        assertThat(serializer.canSerialize(StringValue.class)).isTrue();
        
        // Should not serialize POJOs
        assertThat(serializer.canSerialize(TestEventModels.SimpleTestEvent.class)).isFalse();
        assertThat(serializer.canSerialize(String.class)).isFalse();
        assertThat(serializer.canSerialize(int.class)).isFalse();
    }

    @Test
    @DisplayName("Should throw SerializationException for non-Message objects")
    void shouldThrowSerializationExceptionForNonMessageObjects() {
        // Arrange
        TestEventModels.SimpleTestEvent pojo = TestEventModels.SimpleTestEvent.create("test");
        
        // Act & Assert
        assertThatThrownBy(() -> serializer.serialize(pojo))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("ProtobufMessageSerializer can only serialize Protocol Buffer Message objects")
                .hasMessageContaining("Use JsonMessageSerializer for POJOs");
    }

    @Test
    @DisplayName("Should throw SerializationException when deserializing to non-Message class")
    void shouldThrowSerializationExceptionWhenDeserializingToNonMessageClass() {
        // Arrange
        byte[] data = "test data".getBytes();
        
        // Act & Assert
        assertThatThrownBy(() -> serializer.deserialize(data, String.class))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("ProtobufMessageSerializer can only deserialize to Protocol Buffer Message classes")
                .hasMessageContaining("Use JsonMessageSerializer for POJOs");
    }

    @Test
    @DisplayName("Should throw SerializationException for invalid Protocol Buffer data")
    void shouldThrowSerializationExceptionForInvalidProtocolBufferData() {
        // Arrange
        byte[] invalidData = "invalid protobuf data".getBytes();
        
        // Act & Assert
        assertThatThrownBy(() -> serializer.deserialize(invalidData, StringValue.class))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to parse Protocol Buffer data");
    }

    @Test
    @DisplayName("Should produce compact binary output")
    void shouldProduceCompactBinaryOutput() throws SerializationException {
        // Arrange
        StringValue message = StringValue.of("This is a test message for Protocol Buffers serialization");

        // Act
        byte[] protobufSerialized = serializer.serialize(message);

        // Assert
        assertThat(protobufSerialized.length).isGreaterThan(0);
        // Protocol Buffers should produce reasonably compact output
        assertThat(protobufSerialized.length).isLessThan(100);
    }

    @Test
    @DisplayName("Should handle round-trip serialization correctly")
    void shouldHandleRoundTripSerializationCorrectly() throws SerializationException {
        // Arrange
        StringValue original = StringValue.of("Round trip test message");
        
        // Act
        byte[] serialized = serializer.serialize(original);
        StringValue deserialized = serializer.deserialize(serialized, StringValue.class);
        byte[] reSerialized = serializer.serialize(deserialized);
        
        // Assert
        assertThat(deserialized.getValue()).isEqualTo(original.getValue());
        assertThat(reSerialized).isEqualTo(serialized);
    }
}
