/*
 * Copyright 2025 Firefly Software Solutions Inc
 */
package com.firefly.common.eda.dedup;

import com.firefly.common.eda.properties.EdaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationServiceTest {

    private EdaProperties props;

    @BeforeEach
    void setup() {
        props = new EdaProperties();
        // Ensure consumer object exists and defaults applied
        // dedup is disabled by default (per implementation), we'll toggle per test
    }

    private DeduplicationService serviceWithNoCache() {
        ObjectProvider<?> nullProvider = new ObjectProvider<>() {
            @Override public Object getObject(Object... args) { return null; }
            @Override public Object getIfAvailable() { return null; }
            @Override public Object getIfUnique() { return null; }
            @Override public Object getObject() { return null; }
            @Override public void forEach(java.util.function.Consumer action) {}
            @Override public java.util.Iterator iterator() { return java.util.Collections.emptyIterator(); }
            @Override public java.util.stream.Stream stream() { return java.util.stream.Stream.empty(); }
            @Override public java.util.stream.Stream orderedStream() { return java.util.stream.Stream.empty(); }
        };
        @SuppressWarnings({"rawtypes", "unchecked"})
        ObjectProvider cacheProvider = (ObjectProvider) nullProvider;
        return new DeduplicationService(cacheProvider, props);
    }

    @Test
    @DisplayName("When dedup disabled, tryAcquire always returns true")
    void disabledReturnsTrue() {
        props.getConsumer().getDedup().setEnabled(false);
        DeduplicationService svc = serviceWithNoCache();

        StepVerifier.create(svc.tryAcquire(new Object(), Map.of()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("When cache manager missing, service fails open and returns true")
    void missingCacheManagerFailOpen() {
        props.getConsumer().getDedup().setEnabled(true);
        DeduplicationService svc = serviceWithNoCache();

        StepVerifier.create(svc.tryAcquire(new Object(), Map.of()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Key building prefers transaction-id header")
    void keyBuildingPrefersTransactionId() {
        DeduplicationService svc = serviceWithNoCache();
        String prefix = "eda:dedup:";

        Map<String, Object> headers = new HashMap<>();
        headers.put("transactionId", "tx-ABC");
        headers.put("message_id", "m-1");
        String key = svc.buildDedupKey(new Object(), headers, prefix);
        assertThat(key).isEqualTo(prefix + "tx-ABC");
    }

    @Test
    @DisplayName("Kafka tuple fallback when no stable id headers present")
    void kafkaTupleFallback() {
        DeduplicationService svc = serviceWithNoCache();
        String prefix = "eda:dedup:";

        Map<String, Object> headers = Map.of(
                "kafka.topic", "orders",
                "kafka.partition", 3,
                "kafka.offset", 42L
        );
        String key = svc.buildDedupKey(new Object(), headers, prefix);
        assertThat(key).isEqualTo(prefix + "kafka:orders:3:42");
    }

    @Test
    @DisplayName("RabbitMQ tuple fallback when no stable id headers present")
    void rabbitTupleFallback() {
        DeduplicationService svc = serviceWithNoCache();
        String prefix = "eda:dedup:";

        Map<String, Object> headers = Map.of(
                "amqp_receivedExchange", "events",
                "amqp_receivedRoutingKey", "order.created",
                "amqp_deliveryTag", 101L
        );
        String key = svc.buildDedupKey(new Object(), headers, prefix);
        assertThat(key).isEqualTo(prefix + "rabbit:events:order.created:101");
    }

    @Test
    @DisplayName("Hash fallback produces deterministic non-empty key when no headers available")
    void hashFallback() {
        DeduplicationService svc = serviceWithNoCache();
        String prefix = "eda:dedup:";
        String key1 = svc.buildDedupKey("event-x", Map.of(), prefix);
        String key2 = svc.buildDedupKey("event-x", Map.of(), prefix);
        assertThat(key1).isNotBlank();
        assertThat(key1).startsWith(prefix);
        assertThat(key1).isEqualTo(key2);
        // Hash should look hex-like and long enough (SHA-256 -> 64 chars)
        assertThat(key1.substring(prefix.length()).length()).isGreaterThanOrEqualTo(32);
    }
}
