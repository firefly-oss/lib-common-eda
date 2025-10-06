package com.firefly.common.eda.event;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Unified event envelope for both publishing and consuming events.
 * <p>
 * This class provides a consistent structure for events across all messaging platforms
 * and supports both publisher and consumer use cases with optional fields.
 * <p>
 * The envelope contains:
 * <ul>
 *   <li>Core event data: destination, eventType, payload, headers</li>
 *   <li>Metadata: timestamp, transactionId</li>
 *   <li>Publisher context: publisherType, connectionId</li>
 *   <li>Consumer context: consumerType, ackCallback</li>
 * </ul>
 */
public record EventEnvelope(
        /**
         * The destination where the event was published or consumed from.
         * This could be a topic, queue, exchange, or any messaging destination.
         */
        String destination,

        /**
         * The type of the event, typically the class name or a custom event type.
         */
        String eventType,

        /**
         * The actual event payload/data.
         */
        Object payload,

        /**
         * Optional transaction ID for correlation across distributed systems.
         */
        String transactionId,

        /**
         * Transport-specific headers from the messaging platform.
         */
        Map<String, Object> headers,

        /**
         * Event metadata containing business and technical information.
         */
        EventMetadata metadata,

        /**
         * Timestamp when the event was created or processed.
         */
        Instant timestamp,

        /**
         * The type of publisher that created this event (optional for consumer-only scenarios).
         */
        String publisherType,

        /**
         * The type of consumer that received this event (optional for publisher-only scenarios).
         */
        String consumerType,

        /**
         * Connection ID used for publishing or consuming (optional).
         */
        String connectionId,

        /**
         * Acknowledgment callback for consumer scenarios (optional).
         */
        AckCallback ackCallback
) {

    /**
     * Creates an EventEnvelope for publishing scenarios.
     */
    public static EventEnvelope forPublishing(
            String destination,
            String eventType,
            Object payload,
            String transactionId,
            Map<String, Object> headers,
            EventMetadata metadata,
            Instant timestamp,
            String publisherType,
            String connectionId) {
        return new EventEnvelope(
                destination,
                eventType,
                payload,
                transactionId,
                headers,
                metadata,
                timestamp,
                publisherType,
                null, // consumerType
                connectionId,
                null  // ackCallback
        );
    }

    /**
     * Creates an EventEnvelope for consuming scenarios.
     */
    public static EventEnvelope forConsuming(
            String destination,
            String eventType,
            Object payload,
            String transactionId,
            Map<String, Object> headers,
            EventMetadata metadata,
            Instant timestamp,
            String consumerType,
            String connectionId,
            AckCallback ackCallback) {
        return new EventEnvelope(
                destination,
                eventType,
                payload,
                transactionId,
                headers,
                metadata,
                timestamp,
                null, // publisherType
                consumerType,
                connectionId,
                ackCallback
        );
    }

    /**
     * Creates a minimal EventEnvelope with required fields only.
     */
    public static EventEnvelope minimal(
            String destination,
            String eventType,
            Object payload) {
        return new EventEnvelope(
                destination,
                eventType,
                payload,
                null, // transactionId
                Map.of(), // headers
                EventMetadata.empty(), // metadata
                Instant.now(),
                null, // publisherType
                null, // consumerType
                null, // connectionId
                null  // ackCallback
        );
    }

    /**
     * Creates an EventEnvelope for publishing with simplified parameters.
     */
    public static EventEnvelope forPublishing(
            String destination,
            String eventType,
            Object payload,
            String publisherType) {
        return forPublishing(
                destination,
                eventType,
                payload,
                null, // transactionId
                Map.of(), // headers
                EventMetadata.empty(), // metadata
                Instant.now(),
                publisherType,
                "default" // connectionId
        );
    }

    /**
     * Creates an EventEnvelope for consuming with simplified parameters.
     */
    public static EventEnvelope forConsuming(
            String destination,
            String eventType,
            Object payload,
            String consumerType,
            AckCallback ackCallback) {
        return forConsuming(
                destination,
                eventType,
                payload,
                null, // transactionId
                Map.of(), // headers
                EventMetadata.empty(), // metadata
                Instant.now(),
                consumerType,
                "default", // connectionId
                ackCallback
        );
    }

    /**
     * Acknowledges successful processing of this event.
     * Only available for consumer scenarios.
     */
    public Mono<Void> acknowledge() {
        return ackCallback != null ? ackCallback.acknowledge() : Mono.empty();
    }

    /**
     * Negatively acknowledges this event, indicating processing failure.
     * Only available for consumer scenarios.
     */
    public Mono<Void> reject(Throwable error) {
        return ackCallback != null ? ackCallback.reject(error) : Mono.empty();
    }

    /**
     * Creates a copy of this envelope with a different payload.
     * Useful for transforming events while preserving metadata.
     */
    public EventEnvelope withPayload(Object newPayload) {
        return new EventEnvelope(
                destination,
                eventType,
                newPayload,
                transactionId,
                headers,
                metadata,
                timestamp,
                publisherType,
                consumerType,
                connectionId,
                ackCallback
        );
    }

    /**
     * Creates a copy of this envelope with additional headers.
     */
    public EventEnvelope withHeaders(Map<String, Object> additionalHeaders) {
        Map<String, Object> newHeaders = new java.util.HashMap<>(headers != null ? headers : Map.of());
        if (additionalHeaders != null) {
            newHeaders.putAll(additionalHeaders);
        }
        return new EventEnvelope(
                destination,
                eventType,
                payload,
                transactionId,
                newHeaders,
                metadata,
                timestamp,
                publisherType,
                consumerType,
                connectionId,
                ackCallback
        );
    }

    /**
     * Creates a copy of this envelope with different metadata.
     */
    public EventEnvelope withMetadata(EventMetadata newMetadata) {
        return new EventEnvelope(
                destination,
                eventType,
                payload,
                transactionId,
                headers,
                newMetadata,
                timestamp,
                publisherType,
                consumerType,
                connectionId,
                ackCallback
        );
    }

    /**
     * Checks if this envelope is from a publisher context.
     */
    public boolean isFromPublisher() {
        return publisherType != null;
    }

    /**
     * Checks if this envelope is from a consumer context.
     */
    public boolean isFromConsumer() {
        return consumerType != null;
    }

    /**
     * Checks if this envelope supports acknowledgment.
     */
    public boolean supportsAcknowledgment() {
        return ackCallback != null;
    }

    /**
     * Validates that the envelope has the minimum required fields.
     */
    public void validate() {
        Objects.requireNonNull(destination, "destination cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    /**
     * Callback interface for acknowledging event processing.
     */
    public interface AckCallback {
        /**
         * Acknowledges successful processing of the event.
         */
        Mono<Void> acknowledge();

        /**
         * Negatively acknowledges the event, indicating processing failure.
         */
        Mono<Void> reject(Throwable error);
    }

    @Override
    public String toString() {
        return String.format("EventEnvelope{destination='%s', eventType='%s', payload=%s, timestamp=%s, publisherType='%s', consumerType='%s'}",
                destination, eventType, payload != null ? payload.getClass().getSimpleName() : "null",
                timestamp, publisherType, consumerType);
    }

    /**
     * Event metadata containing business and technical information.
     */
    public record EventMetadata(
            /**
             * Business correlation ID for tracking across services.
             */
            String correlationId,

            /**
             * Causation ID linking this event to the command that caused it.
             */
            String causationId,

            /**
             * Version of the event schema/structure.
             */
            String version,

            /**
             * Source system or service that generated the event.
             */
            String source,

            /**
             * User or system that initiated the action leading to this event.
             */
            String userId,

            /**
             * Session ID for user interactions.
             */
            String sessionId,

            /**
             * Tenant ID for multi-tenant systems.
             */
            String tenantId,

            /**
             * Additional custom metadata as key-value pairs.
             */
            Map<String, Object> custom
    ) {
        /**
         * Creates minimal metadata with only correlation ID.
         */
        public static EventMetadata minimal(String correlationId) {
            return new EventMetadata(
                    correlationId,
                    null, // causationId
                    null, // version
                    null, // source
                    null, // userId
                    null, // sessionId
                    null, // tenantId
                    Map.of() // custom
            );
        }

        /**
         * Creates empty metadata.
         */
        public static EventMetadata empty() {
            return new EventMetadata(
                    null, null, null, null, null, null, null, Map.of()
            );
        }

        /**
         * Creates metadata with custom properties.
         */
        public static EventMetadata withCustom(Map<String, Object> custom) {
            return new EventMetadata(
                    null, null, null, null, null, null, null,
                    custom != null ? custom : Map.of()
            );
        }

        /**
         * Creates a copy with additional custom metadata.
         */
        public EventMetadata withCustom(String key, Object value) {
            Map<String, Object> newCustom = new java.util.HashMap<>(custom != null ? custom : Map.of());
            newCustom.put(key, value);
            return new EventMetadata(
                    correlationId, causationId, version, source, userId, sessionId, tenantId, newCustom
            );
        }
    }
}
