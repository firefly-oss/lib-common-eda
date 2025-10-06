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
 * Enumeration of supported serialization formats.
 */
public enum SerializationFormat {
    
    /**
     * JavaScript Object Notation (JSON).
     * <p>
     * Human-readable text format that is widely supported.
     * Good for debugging and interoperability.
     */
    JSON("application/json"),
    
    /**
     * Apache Avro binary format.
     * <p>
     * Compact binary format with schema evolution support.
     * Excellent for high-throughput scenarios.
     */
    AVRO("application/avro"),
    
    /**
     * Google Protocol Buffers.
     * <p>
     * Efficient binary format with strong typing.
     * Great for service-to-service communication.
     */
    PROTOBUF("application/x-protobuf"),
    
    /**
     * Apache Thrift binary format.
     * <p>
     * Cross-language serialization framework.
     * Good for polyglot environments.
     */
    THRIFT("application/x-thrift"),
    
    /**
     * MessagePack binary format.
     * <p>
     * Efficient binary format similar to JSON.
     * More compact than JSON with similar features.
     */
    MESSAGEPACK("application/x-msgpack"),
    
    /**
     * Java native serialization.
     * <p>
     * Built-in Java serialization mechanism.
     * Only suitable for Java-to-Java communication.
     */
    JAVA_SERIALIZATION("application/x-java-serialized-object"),
    
    /**
     * Plain string format.
     * <p>
     * Simple string representation.
     * Useful for simple text-based messages.
     */
    STRING("text/plain"),
    
    /**
     * Raw byte array format.
     * <p>
     * No serialization performed.
     * Payload must already be a byte array.
     */
    BINARY("application/octet-stream"),
    
    /**
     * Custom serialization format.
     * <p>
     * For custom serializer implementations.
     */
    CUSTOM("application/octet-stream");
    
    private final String contentType;
    
    SerializationFormat(String contentType) {
        this.contentType = contentType;
    }
    
    /**
     * Gets the MIME content type for this format.
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * Gets the format name in lowercase.
     *
     * @return the format name
     */
    public String getFormatName() {
        return name().toLowerCase();
    }
    
    /**
     * Checks if this format is binary (non-human-readable).
     *
     * @return true if the format is binary
     */
    public boolean isBinary() {
        return switch (this) {
            case AVRO, PROTOBUF, THRIFT, MESSAGEPACK, JAVA_SERIALIZATION, BINARY -> true;
            case JSON, STRING, CUSTOM -> false;
        };
    }
    
    /**
     * Checks if this format supports schema evolution.
     *
     * @return true if schema evolution is supported
     */
    public boolean supportsSchemaEvolution() {
        return switch (this) {
            case AVRO, PROTOBUF, THRIFT -> true;
            case JSON, MESSAGEPACK, JAVA_SERIALIZATION, STRING, BINARY, CUSTOM -> false;
        };
    }
}