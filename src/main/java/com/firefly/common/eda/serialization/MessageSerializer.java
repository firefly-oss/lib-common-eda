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

/**
 * Interface for serializing message payloads to byte arrays.
 * <p>
 * Implementations provide different serialization strategies
 * such as JSON, Avro, Protocol Buffers, etc.
 */
public interface MessageSerializer {

    /**
     * Serializes the given object to a byte array.
     *
     * @param payload the object to serialize
     * @return the serialized bytes
     * @throws SerializationException if serialization fails
     */
    byte[] serialize(Object payload) throws SerializationException;
    
    /**
     * Deserializes the given byte array to an object of the specified type.
     *
     * @param data the serialized data
     * @param targetType the target class to deserialize to
     * @param <T> the type of the target object
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException;
    
    /**
     * Deserializes the given string to an object of the specified type.
     * This is a convenience method for text-based serializers.
     *
     * @param data the serialized data as string
     * @param targetType the target class to deserialize to
     * @param <T> the type of the target object
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    default <T> T deserialize(String data, Class<T> targetType) throws SerializationException {
        return deserialize(data.getBytes(java.nio.charset.StandardCharsets.UTF_8), targetType);
    }

    /**
     * Gets the format identifier for this serializer.
     *
     * @return the format identifier (e.g., "json", "avro", "protobuf")
     */
    SerializationFormat getFormat();

    /**
     * Gets the content type for this serializer.
     *
     * @return the MIME content type (e.g., "application/json")
     */
    String getContentType();

    /**
     * Checks if this serializer can handle the given object type.
     *
     * @param type the object type to check
     * @return true if the serializer can handle this type
     */
    default boolean canSerialize(Class<?> type) {
        return true; // Default implementation accepts all types
    }

    /**
     * Gets the priority of this serializer for auto-selection.
     * <p>
     * Higher numbers indicate higher priority.
     *
     * @return the priority (default is 0)
     */
    default int getPriority() {
        return 0;
    }
}