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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON-based message serializer using Jackson ObjectMapper.
 * <p>
 * This serializer converts objects to JSON format and then to UTF-8 bytes.
 * It provides good human readability and wide compatibility.
 */
@Component
@RequiredArgsConstructor
public class JsonMessageSerializer implements MessageSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public byte[] serialize(Object payload) throws SerializationException {
        if (payload == null) {
            return "null".getBytes(StandardCharsets.UTF_8);
        }

        if (payload instanceof String stringPayload) {
            return stringPayload.getBytes(StandardCharsets.UTF_8);
        }

        if (payload instanceof byte[] bytePayload) {
            return bytePayload;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        
        if (targetType == String.class) {
            return targetType.cast(new String(data, StandardCharsets.UTF_8));
        }
        
        if (targetType == byte[].class) {
            return targetType.cast(data);
        }
        
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, targetType);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize JSON to " + targetType.getName(), e);
        }
    }
    
    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.JSON;
    }

    @Override
    public String getContentType() {
        return SerializationFormat.JSON.getContentType();
    }

    @Override
    public int getPriority() {
        return 100; // High priority as it's the default
    }
}