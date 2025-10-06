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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Google Protocol Buffers message serializer.
 * <p>
 * This serializer provides efficient binary serialization using Google Protocol Buffers.
 * It supports Protocol Buffer generated classes that extend {@link Message}.
 * <p>
 * Features:
 * <ul>
 *   <li>Compact binary format</li>
 *   <li>Strong typing</li>
 *   <li>Schema evolution support</li>
 *   <li>High performance</li>
 *   <li>Cross-language compatibility</li>
 * </ul>
 * <p>
 * Note: This serializer only works with classes generated from .proto files
 * that extend {@link Message}. For POJOs, use {@link JsonMessageSerializer}
 * or {@link AvroMessageSerializer}.
 */
@Component
@ConditionalOnClass(Message.class)
@Slf4j
public class ProtobufMessageSerializer implements MessageSerializer {

    @Override
    public byte[] serialize(Object payload) throws SerializationException {
        if (payload == null) {
            return new byte[0];
        }

        if (payload instanceof byte[] bytePayload) {
            return bytePayload;
        }

        if (!(payload instanceof Message message)) {
            throw new SerializationException(
                "ProtobufMessageSerializer can only serialize Protocol Buffer Message objects. " +
                "Received: " + payload.getClass().getName() + 
                ". Use JsonMessageSerializer for POJOs."
            );
        }

        try {
            byte[] result = message.toByteArray();
            log.debug("Serialized Protocol Buffer message: {} ({} bytes)", 
                     message.getClass().getSimpleName(), result.length);
            return result;
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize Protocol Buffer message", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        
        if (targetType == byte[].class) {
            return (T) data;
        }

        if (!Message.class.isAssignableFrom(targetType)) {
            throw new SerializationException(
                "ProtobufMessageSerializer can only deserialize to Protocol Buffer Message classes. " +
                "Received: " + targetType.getName() + 
                ". Use JsonMessageSerializer for POJOs."
            );
        }

        try {
            // Get the parser for the target type using reflection
            Parser<? extends Message> parser = getParserForType(targetType);
            Message message = parser.parseFrom(data);
            log.debug("Deserialized Protocol Buffer message: {} ({} bytes)", 
                     targetType.getSimpleName(), data.length);
            return (T) message;
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Failed to parse Protocol Buffer data for " + targetType.getName(), e);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize Protocol Buffer data to " + targetType.getName(), e);
        }
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.PROTOBUF;
    }

    @Override
    public String getContentType() {
        return SerializationFormat.PROTOBUF.getContentType();
    }

    @Override
    public int getPriority() {
        return 90; // High priority for binary efficiency, slightly higher than Avro
    }

    @Override
    public boolean canSerialize(Class<?> type) {
        // Only Protocol Buffer Message classes
        return Message.class.isAssignableFrom(type);
    }

    /**
     * Gets the parser for a Protocol Buffer message type using reflection.
     * <p>
     * Protocol Buffer generated classes have a static parser() method that returns
     * a Parser instance for that specific message type.
     *
     * @param messageType the Protocol Buffer message class
     * @return the parser for the message type
     * @throws SerializationException if the parser cannot be obtained
     */
    @SuppressWarnings("unchecked")
    private Parser<? extends Message> getParserForType(Class<?> messageType) throws SerializationException {
        try {
            // Protocol Buffer generated classes have a static parser() method
            Method parserMethod = messageType.getMethod("parser");
            Object parser = parserMethod.invoke(null);
            
            if (!(parser instanceof Parser)) {
                throw new SerializationException(
                    "Expected Parser instance from parser() method, got: " + 
                    (parser != null ? parser.getClass().getName() : "null")
                );
            }
            
            return (Parser<? extends Message>) parser;
        } catch (NoSuchMethodException e) {
            throw new SerializationException(
                "Class " + messageType.getName() + " does not have a parser() method. " +
                "Make sure it's a properly generated Protocol Buffer class.", e
            );
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SerializationException(
                "Failed to invoke parser() method on " + messageType.getName(), e
            );
        }
    }
}
