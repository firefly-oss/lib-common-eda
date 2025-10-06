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

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Apache Avro-based message serializer.
 * <p>
 * This serializer provides efficient binary serialization using Apache Avro.
 * It supports both specific records (generated from schema) and reflection-based
 * serialization for POJOs.
 * <p>
 * Features:
 * <ul>
 *   <li>Compact binary format</li>
 *   <li>Schema evolution support</li>
 *   <li>High performance</li>
 *   <li>Support for both specific and generic records</li>
 * </ul>
 */
@Component
@ConditionalOnClass(Schema.class)
@Slf4j
public class AvroMessageSerializer implements MessageSerializer {

    @Override
    public byte[] serialize(Object payload) throws SerializationException {
        if (payload == null) {
            return new byte[0];
        }

        if (payload instanceof byte[] bytePayload) {
            return bytePayload;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            if (payload instanceof SpecificRecord specificRecord) {
                // Handle Avro-generated specific records
                serializeSpecificRecord(specificRecord, outputStream);
            } else if (payload instanceof GenericRecord genericRecord) {
                // Handle generic records
                serializeGenericRecord(genericRecord, outputStream);
            } else {
                // Handle POJOs using reflection
                serializeReflection(payload, outputStream);
            }
            
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize object with Avro", e);
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

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            
            if (SpecificRecord.class.isAssignableFrom(targetType)) {
                // Handle Avro-generated specific records
                return deserializeSpecificRecord(inputStream, targetType);
            } else if (GenericRecord.class.isAssignableFrom(targetType)) {
                // Handle generic records
                return deserializeGenericRecord(inputStream, targetType);
            } else {
                // Handle POJOs using reflection
                return deserializeReflection(inputStream, targetType);
            }
        } catch (IOException | RuntimeException e) {
            throw new SerializationException("Failed to deserialize Avro data to " + targetType.getName(), e);
        }
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.AVRO;
    }

    @Override
    public String getContentType() {
        return SerializationFormat.AVRO.getContentType();
    }

    @Override
    public int getPriority() {
        return 80; // High priority for binary efficiency
    }

    @Override
    public boolean canSerialize(Class<?> type) {
        // Can serialize SpecificRecord, GenericRecord, or simple POJOs
        // Avoid complex types that Avro reflection can't handle well
        return SpecificRecord.class.isAssignableFrom(type) ||
               GenericRecord.class.isAssignableFrom(type) ||
               (!type.isPrimitive() &&
                !type.getName().startsWith("java.time.") && // Avoid Java Time types
                !type.isInterface() &&
                !type.isEnum()); // Keep it simple for reflection
    }

    private void serializeSpecificRecord(SpecificRecord record, ByteArrayOutputStream outputStream) throws IOException {
        Schema schema = record.getSchema();
        DatumWriter<SpecificRecord> datumWriter = new SpecificDatumWriter<>(schema);
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datumWriter.write(record, encoder);
        encoder.flush();
        log.debug("Serialized SpecificRecord with schema: {}", schema.getName());
    }

    private void serializeGenericRecord(GenericRecord record, ByteArrayOutputStream outputStream) throws IOException {
        Schema schema = record.getSchema();
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datumWriter.write(record, encoder);
        encoder.flush();
        log.debug("Serialized GenericRecord with schema: {}", schema.getName());
    }

    private void serializeReflection(Object payload, ByteArrayOutputStream outputStream) throws IOException {
        Schema schema = ReflectData.get().getSchema(payload.getClass());
        DatumWriter<Object> datumWriter = new ReflectDatumWriter<>(schema);
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datumWriter.write(payload, encoder);
        encoder.flush();
        log.debug("Serialized POJO {} using reflection with schema: {}", 
                 payload.getClass().getSimpleName(), schema.getName());
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeSpecificRecord(ByteArrayInputStream inputStream, Class<T> targetType) throws IOException {
        try {
            // Get schema from the class
            SpecificRecord instance = (SpecificRecord) targetType.getDeclaredConstructor().newInstance();
            Schema schema = instance.getSchema();
            
            DatumReader<SpecificRecord> datumReader = new SpecificDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(inputStream, null);
            SpecificRecord result = datumReader.read(null, decoder);
            log.debug("Deserialized SpecificRecord with schema: {}", schema.getName());
            return (T) result;
        } catch (Exception e) {
            throw new IOException("Failed to deserialize SpecificRecord", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeGenericRecord(ByteArrayInputStream inputStream, Class<T> targetType) throws IOException {
        // For GenericRecord, we need the schema to be provided somehow
        // This is a simplified implementation - in practice, you'd need schema registry
        throw new IOException("GenericRecord deserialization requires schema information");
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeReflection(ByteArrayInputStream inputStream, Class<T> targetType) throws IOException {
        Schema schema = ReflectData.get().getSchema(targetType);
        DatumReader<T> datumReader = new ReflectDatumReader<>(schema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(inputStream, null);
        T result = datumReader.read(null, decoder);
        log.debug("Deserialized POJO {} using reflection with schema: {}", 
                 targetType.getSimpleName(), schema.getName());
        return result;
    }
}
