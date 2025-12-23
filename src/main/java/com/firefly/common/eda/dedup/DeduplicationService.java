/*
 * Copyright 2025 Firefly Software Solutions Inc
 */
package com.firefly.common.eda.dedup;

import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.common.eda.properties.EdaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a cache-backed deduplication guard to avoid processing the same event twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final ObjectProvider<FireflyCacheManager> cacheManagerProvider;
    private final EdaProperties edaProperties;

    /**
     * Attempts to claim processing for the given event. Returns true if the event should be processed
     * (first time seen within TTL), or false if it was already processed recently.
     */
    public Mono<Boolean> tryAcquire(Object event, Map<String, Object> headers) {
        EdaProperties.Consumer.Dedup dedup = edaProperties.getConsumer().getDedup();
        if (!dedup.isEnabled()) {
            return Mono.just(true);
        }

        FireflyCacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            log.warn("[Dedup] FireflyCacheManager not available. Proceeding without deduplication (fail-open).");
            return Mono.just(true);
        }

        String key = buildDedupKey(event, headers, dedup.getKeyPrefix());
        Duration ttl = Optional.ofNullable(dedup.getTtl()).orElse(Duration.ofMinutes(10));

        return cacheManager.get(key, String.class)
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        log.debug("[Dedup] Duplicate detected. key={}", key);
                        return Mono.just(false);
                    }
                    // Write a simple marker value and succeed
                    return cacheManager.put(key, "1", ttl)
                            .thenReturn(true)
                            .onErrorResume(err -> {
                                // Be fail-open to avoid message loss due to cache outages
                                log.warn("[Dedup] Failed to put key in cache (fail-open). key={}, error={}", key, err.toString());
                                return Mono.just(true);
                            });
                })
                .onErrorResume(err -> {
                    // Be fail-open if cache read fails
                    log.warn("[Dedup] Failed to read key from cache (fail-open). error={}", err.toString());
                    return Mono.just(true);
                });
    }

    public String buildDedupKey(Object event, Map<String, Object> headers, String prefix) {
        String base = extractStableId(headers)
                .orElseGet(() -> fallbackFromHeadersOrEvent(headers, event));
        return prefix + base;
    }

    private Optional<String> extractStableId(Map<String, Object> headers) {
        if (headers == null) return Optional.empty();
        // Common header variants for transaction/message IDs
        String[] keys = new String[]{
                "transaction-id", "transactionId", "x-transaction-id",
                // RabbitMQ common properties mirrored into headers in this lib
                "message_id", "correlation_id",
                // Kafka custom/user headers
                "event_id", "event-id"
        };
        for (String k : keys) {
            Object v = headers.get(k);
            if (v != null) return Optional.of(v.toString());
        }
        return Optional.empty();
    }

    private String fallbackFromHeadersOrEvent(Map<String, Object> headers, Object event) {
        try {
            // Kafka specific tuple
            Object topic = headers != null ? headers.get("kafka.topic") : null;
            Object partition = headers != null ? headers.get("kafka.partition") : null;
            Object offset = headers != null ? headers.get("kafka.offset") : null;
            if (topic != null && partition != null && offset != null) {
                return String.format("kafka:%s:%s:%s", topic, partition, offset);
            }
            // RabbitMQ specific identifiers
            Object exchange = headers != null ? headers.get("amqp_receivedExchange") : null;
            Object routingKey = headers != null ? headers.get("amqp_receivedRoutingKey") : null;
            Object deliveryTag = headers != null ? headers.get("amqp_deliveryTag") : null;
            if (exchange != null && routingKey != null && deliveryTag != null) {
                return String.format("rabbit:%s:%s:%s", exchange, routingKey, deliveryTag);
            }
        } catch (Exception ignore) {
            // noop
        }

        // Final fallback: hash of event class + toString to reduce collisions (best-effort only)
        String fingerprint = event == null ? "null" : event.getClass().getName() + ":" + String.valueOf(event);
        return sha256(fingerprint);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
