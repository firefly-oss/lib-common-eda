# API Reference

Complete API reference for the Firefly Event Driven Architecture Library.

---

## ⚠️ Configuration Namespace Warning

**This library follows hexagonal architecture principles.**

✅ **ALWAYS use `firefly.eda.*` configuration properties**
❌ **NEVER use `spring.kafka.*` or `spring.rabbitmq.*` properties**

The library provides complete abstraction from messaging platform implementations. All provider-specific configurations are managed internally. See [Configuration Reference](CONFIGURATION.md) for details.

---

## Table of Contents

- [Core Interfaces](#core-interfaces)
- [Annotations](#annotations)
- [Configuration Properties](#configuration-properties)
- [Event Types](#event-types)
- [Publisher Types](#publisher-types)
- [Error Handling](#error-handling)
- [Filters](#filters)
- [Metrics](#metrics)
- [Health Indicators](#health-indicators)

## Core Interfaces

### EventPublisher

The main interface for publishing events to messaging systems.

```java
public interface EventPublisher {
    /**
     * Publishes an event to the specified destination.
     *
     * @param event the event object to publish (must be serializable)
     * @param destination the destination to publish to (topic, queue, exchange, etc.)
     * @param headers custom headers to include with the message (optional)
     * @return a Mono that completes when the event is published
     */
    Mono<Void> publish(Object event, String destination, Map<String, Object> headers);

    /**
     * Publishes an event with simplified parameters.
     *
     * @param event the event object to publish
     * @param destination the destination to publish to
     * @return a Mono that completes when the event is published
     */
    default Mono<Void> publish(Object event, String destination) {
        return publish(event, destination, null);
    }

    /**
     * Checks if this publisher is available and properly configured.
     *
     * @return true if the publisher is ready for use
     */
    boolean isAvailable();

    /**
     * Gets the publisher type identifier.
     *
     * @return the publisher type
     */
    PublisherType getPublisherType();

    /**
     * Gets the default destination for this publisher if none is specified.
     *
     * @return the default destination, or null if none is configured
     */
    default String getDefaultDestination() {
        return null;
    }

    /**
     * Gets health information about this publisher.
     *
     * @return a Mono containing health status
     */
    default Mono<PublisherHealth> getHealth() {
        return Mono.just(PublisherHealth.builder()
                .publisherType(getPublisherType())
                .available(isAvailable())
                .status(isAvailable() ? "UP" : "DOWN")
                .build());
    }

    /**
     * Performs any necessary cleanup when the publisher is no longer needed.
     */
    default void close() {
        // Default implementation does nothing
    }
}
```

### EventConsumer

Interface for consuming events from messaging systems.

```java
public interface EventConsumer {
    /**
     * Starts consuming events from the configured destinations.
     *
     * @return a Flux of incoming events
     */
    Flux<EventEnvelope> consume();

    /**
     * Starts consuming events from specific destinations.
     *
     * @param destinations the destinations to consume from
     * @return a Flux of incoming events
     */
    Flux<EventEnvelope> consume(String... destinations);

    /**
     * Starts the consumer.
     *
     * @return a Mono that completes when the consumer is started
     */
    Mono<Void> start();

    /**
     * Stops the consumer.
     *
     * @return a Mono that completes when the consumer is stopped
     */
    Mono<Void> stop();

    /**
     * Checks if the consumer is currently running.
     *
     * @return true if the consumer is running
     */
    boolean isRunning();

    /**
     * Gets the consumer type identifier.
     *
     * @return the consumer type
     */
    String getConsumerType();

    /**
     * Checks if this consumer is available and properly configured.
     *
     * @return true if the consumer is ready for use
     */
    boolean isAvailable();

    /**
     * Gets health information about this consumer.
     *
     * @return a Mono containing health status
     */
    default Mono<ConsumerHealth> getHealth() {
        return Mono.just(ConsumerHealth.builder()
                .consumerType(getConsumerType())
                .available(isAvailable())
                .running(isRunning())
                .status(isAvailable() && isRunning() ? "UP" : "DOWN")
                .build());
    }

    /**
     * Performs any necessary cleanup when the consumer is no longer needed.
     */
    default void close() {
        // Default implementation does nothing
    }
}
```

### EventPublisherFactory

Factory for creating and managing event publishers.

```java
@Component
public class EventPublisherFactory {
    /**
     * Gets an event publisher for the specified type and connection ID.
     *
     * @param publisherType the publisher type
     * @param connectionId the connection ID (null for default)
     * @return the event publisher or null if not available
     */
    public EventPublisher getPublisher(PublisherType publisherType, String connectionId);

    /**
     * Gets an event publisher for the specified type using the default connection.
     *
     * @param publisherType the publisher type
     * @return the event publisher or null if not available
     */
    public EventPublisher getPublisher(PublisherType publisherType);

    /**
     * Gets all available publishers with their types.
     *
     * @return map of publisher types to publisher instances
     */
    public Map<String, EventPublisher> getAvailablePublishers();

    /**
     * Checks if a specific publisher type is available.
     *
     * @param publisherType the publisher type to check
     * @return true if available
     */
    public boolean isPublisherAvailable(PublisherType publisherType);

    /**
     * Gets health information for all publishers.
     *
     * @return map of publisher types to health information
     */
    public Map<String, PublisherHealth> getPublishersHealth();

    /**
     * Gets the default publisher based on configuration.
     *
     * @return the default publisher
     */
    public EventPublisher getDefaultPublisher();
}
```

### MessageSerializer

Interface for serializing message payloads.

```java
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
     *
     * @return the priority (default is 0)
     */
    default int getPriority() {
        return 0;
    }
}
```

### EventEnvelope

Unified event container for both publishing and consuming events.

```java
public record EventEnvelope(
    String destination,
    String eventType, 
    Object payload,
    String transactionId,
    Map<String, Object> headers,
    EventMetadata metadata,
    Instant timestamp,
    String publisherType,
    String consumerType,
    String connectionId,
    AckCallback ackCallback
) {
    // Static factory methods
    public static EventEnvelope forPublishing(String destination, String eventType, Object payload, String publisherType);
    public static EventEnvelope forConsuming(String destination, String eventType, Object payload, String consumerType, AckCallback ackCallback);
    public static EventEnvelope minimal(String destination, String eventType, Object payload);
    
    // Acknowledgment methods
    public Mono<Void> acknowledge();
    public Mono<Void> reject(Throwable error);
    
    // Utility methods
    public EventEnvelope withPayload(Object newPayload);
    public EventEnvelope withHeaders(Map<String, Object> additionalHeaders);
    public EventEnvelope withMetadata(EventMetadata newMetadata);
    
    // Validation and checks
    public boolean isFromPublisher();
    public boolean isFromConsumer();
    public boolean supportsAcknowledgment();
    public void validate();
}
```

**EventMetadata Record:**

```java
public record EventMetadata(
    String correlationId,
    String causationId,
    String version,
    String source,
    String userId,
    String sessionId,
    String tenantId,
    Map<String, Object> custom
) {
    public static EventMetadata minimal(String correlationId);
    public static EventMetadata empty();
    public static EventMetadata withCustom(Map<String, Object> custom);
    public EventMetadata withCustom(String key, Object value);
}
```

**Example Usage:**

```java
// For publishing
EventEnvelope envelope = EventEnvelope.forPublishing(
    "order-events",
    "order.created", 
    orderData,
    "KAFKA"
);

// For consuming with acknowledgment
@EventListener(destinations = "order-events")
public Mono<Void> handleOrder(EventEnvelope envelope) {
    OrderData order = (OrderData) envelope.payload();
    return processOrder(order)
        .then(envelope.acknowledge())
        .onErrorResume(error -> envelope.reject(error));
}

// Transforming events
EventEnvelope transformed = envelope
    .withPayload(transformedData)
    .withHeaders(Map.of("processed-by", "service-a"))
    .withMetadata(EventMetadata.minimal("correlation-123"));
```

## Annotations

### @PublishResult

Annotation to automatically publish method results as events.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublishResult {
    /**
     * The type of publisher to use for publishing the result.
     */
    PublisherType publisherType() default PublisherType.AUTO;

    /**
     * The destination to publish to (topic, queue, channel, etc.).
     * Supports SpEL expressions.
     */
    String destination() default "";

    /**
     * The event type to use when publishing.
     * Supports SpEL expressions.
     */
    String eventType() default "";

    /**
     * The connection ID to use for this publisher.
     */
    String connectionId() default "default";

    /**
     * The serializer bean name to use for this publication.
     */
    String serializer() default "";

    /**
     * Whether to publish the result asynchronously.
     */
    boolean async() default true;

    /**
     * The timeout in milliseconds for the publication operation.
     */
    long timeoutMs() default 0;

    /**
     * Whether to publish the result even if the method throws an exception.
     */
    boolean publishOnError() default false;

    /**
     * Condition expression that must evaluate to true for the event to be published.
     * Supports SpEL expressions.
     */
    String condition() default "";

    /**
     * Key expression for partitioning or routing.
     * Supports SpEL expressions.
     */
    String key() default "";

    /**
     * Custom headers to include with the published event.
     * Format: "key1=value1,key2=value2"
     * Values support SpEL expressions.
     */
    String[] headers() default {};
}
```

**Example Usage:**
```java
@PublishResult(
    publisherType = PublisherType.KAFKA,
    destination = "order-events",
    eventType = "order.created",
    condition = "#result.isValid()",
    key = "#result.customerId",
    headers = {"priority=high", "region=#{#result.region}"}
)
public Mono<Order> createOrder(CreateOrderRequest request) {
    return orderService.createOrder(request);
}
```

### @EventListener

Annotation to mark methods as event listeners.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
    /**
     * The destinations to listen on (topics, queues, channels, etc.).
     */
    String[] destinations() default {};

    /**
     * The event types this listener should handle.
     * Supports glob patterns (*, ?, etc.).
     */
    String[] eventTypes() default {};

    /**
     * The consumer type to use for this listener.
     */
    PublisherType consumerType() default PublisherType.AUTO;

    /**
     * The connection ID to use for this listener.
     */
    String connectionId() default "default";

    /**
     * Error handling strategy for failed message processing.
     */
    ErrorHandlingStrategy errorStrategy() default ErrorHandlingStrategy.LOG_AND_CONTINUE;

    /**
     * Maximum number of retry attempts for failed messages.
     */
    int maxRetries() default 3;

    /**
     * Delay between retry attempts in milliseconds.
     */
    long retryDelayMs() default 1000;

    /**
     * Whether to automatically acknowledge successfully processed messages.
     */
    boolean autoAck() default true;

    /**
     * Consumer group ID for this listener.
     */
    String groupId() default "";

    /**
     * Filter expression that must evaluate to true for the event to be processed.
     * Supports SpEL expressions.
     */
    String condition() default "";

    /**
     * Priority of this listener (higher numbers = higher priority).
     */
    int priority() default 0;

    /**
     * Whether this listener should run asynchronously.
     */
    boolean async() default true;

    /**
     * Timeout for event processing in milliseconds.
     */
    long timeoutMs() default 0;
}
```

**Example Usage:**
```java
@EventListener(
    destinations = {"order-events", "payment-events"},
    eventTypes = {"*.created", "*.updated"},
    condition = "#envelope.headers['priority'] == 'HIGH'",
    errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
    maxRetries = 5,
    priority = 100
)
public Mono<Void> handleHighPriorityEvents(EventEnvelope envelope) {
    return processEvent(envelope)
        .then(envelope.acknowledge());
}
```

## Configuration Properties

### EdaProperties

Main configuration properties class.

```java
@ConfigurationProperties(prefix = "firefly.eda")
@Validated
public class EdaProperties {
    /**
     * Whether the EDA library is enabled.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Default publisher type to use when none is specified.
     * Default: PublisherType.AUTO
     */
    private PublisherType defaultPublisherType = PublisherType.AUTO;

    /**
     * Default connection ID to use when none is specified.
     * Default: "default"
     */
    private String defaultConnectionId = "default";

    /**
     * Default destination for events when none is specified.
     * Default: "events"
     */
    private String defaultDestination = "events";

    /**
     * Default serialization format.
     * Default: "json"
     */
    private String defaultSerializationFormat = "json";

    /**
     * Default timeout for publish operations.
     * Default: 30s
     */
    private Duration defaultTimeout = Duration.ofSeconds(30);

    /**
     * Whether to enable metrics collection.
     * Default: true
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to enable health checks.
     * Default: true
     */
    private boolean healthEnabled = true;

    /**
     * Whether to enable tracing integration.
     * Default: true
     */
    private boolean tracingEnabled = true;

    /**
     * Resilience configuration.
     */
    private final Resilience resilience = new Resilience();

    /**
     * Publisher configurations by type.
     */
    private final Publishers publishers = new Publishers();

    /**
     * Consumer configurations.
     */
    private final Consumer consumer = new Consumer();

    /**
     * Step events configuration.
     */
    private final StepEvents stepEvents = new StepEvents();
}
```

### Publisher Configuration

```yaml
firefly:
  eda:
    publishers:
      kafka:
        default:                    # Connection ID
          enabled: true
          bootstrap-servers: localhost:9092
          default-topic: events
          key-serializer: org.apache.kafka.common.serialization.StringSerializer
          value-serializer: org.apache.kafka.common.serialization.StringSerializer
          properties:
            acks: all
            retries: 3
            batch.size: 16384
            linger.ms: 10
            buffer.memory: 33554432
            
      rabbitmq:
        default:
          enabled: true
          host: localhost
          port: 5672
          username: guest
          password: guest
          virtual-host: /
          default-exchange: events
          default-routing-key: event
          properties:
            connection-timeout: 60000
            
      sqs:
        default:
          enabled: true
          region: us-east-1
          access-key-id: ${AWS_ACCESS_KEY_ID:}
          secret-access-key: ${AWS_SECRET_ACCESS_KEY:}
          session-token: ${AWS_SESSION_TOKEN:}
          endpoint-override: ${AWS_ENDPOINT_OVERRIDE:}
          default-queue: events-queue
          
      kinesis:
        default:
          enabled: true
          region: us-east-1
          access-key-id: ${AWS_ACCESS_KEY_ID:}
          secret-access-key: ${AWS_SECRET_ACCESS_KEY:}
          session-token: ${AWS_SESSION_TOKEN:}
          endpoint-override: ${AWS_ENDPOINT_OVERRIDE:}
          default-stream: events-stream
          default-partition-key: default
          
      application-event:
        enabled: true
        default-destination: application-events
```

### Consumer Configuration

```yaml
firefly:
  eda:
    consumer:
      enabled: true
      group-id: my-application
      concurrency: 1
      retry:
        enabled: true
        max-attempts: 3
        initial-delay: 1s
        max-delay: 5m
        multiplier: 2.0

      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          topics: events,notifications
          auto-offset-reset: earliest
          key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
          properties:
            fetch.min.bytes: 1
            fetch.max.wait.ms: 500
            max.partition.fetch.bytes: 1048576

      rabbitmq:
        default:
          enabled: true
          host: localhost
          port: 5672
          username: guest
          password: guest
          virtual-host: /
          queues: events-queue,notifications-queue
          concurrent-consumers: 2
          max-concurrent-consumers: 10
          prefetch-count: 20
          properties:
            acknowledge-mode: auto

      application-event:
        enabled: true

      noop:
        enabled: false
```

### Resilience Configuration

```yaml
firefly:
  eda:
    resilience:
      enabled: true
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 60s
        minimum-number-of-calls: 10
        sliding-window-size: 10
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2.0
        
      rate-limiter:
        enabled: false
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 5s
```

## Event Types

### EventEnvelope

Immutable event container with rich metadata.

```java
public record EventEnvelope(
    String destination,
    String eventType,
    Object payload,
    String transactionId,
    Map<String, Object> headers,
    Instant timestamp,
    String consumerType,
    AckCallback ackCallback
) {
    /**
     * Acknowledges successful processing of this event.
     *
     * @return a Mono that completes when the acknowledgment is processed
     */
    public Mono<Void> acknowledge() {
        return ackCallback != null ? ackCallback.acknowledge() : Mono.empty();
    }

    /**
     * Negatively acknowledges this event, indicating processing failure.
     *
     * @param error the error that occurred during processing
     * @return a Mono that completes when the rejection is processed
     */
    public Mono<Void> reject(Throwable error) {
        return ackCallback != null ? ackCallback.reject(error) : Mono.empty();
    }
    
    /**
     * Callback interface for acknowledging event processing.
     */
    public interface AckCallback {
        Mono<Void> acknowledge();
        Mono<Void> reject(Throwable error);
    }
}
```

### PublisherHealth

Health information for event publishers.

```java
public class PublisherHealth {
    private final PublisherType publisherType;
    private final boolean available;
    private final String status;
    private final String connectionId;
    private final Instant lastChecked;
    private final String errorMessage;
    private final Map<String, Object> details;
    
    // Builder pattern
    public static PublisherHealthBuilder builder() {
        return new PublisherHealthBuilder();
    }
}
```

### ConsumerHealth

Health information for event consumers.

```java
public class ConsumerHealth {
    private final String consumerType;
    private final boolean available;
    private final boolean running;
    private final String status;
    private final Instant lastChecked;
    private final String errorMessage;
    private final Map<String, Object> details;
    
    // Builder pattern
    public static ConsumerHealthBuilder builder() {
        return new ConsumerHealthBuilder();
    }
}
```

## Publisher Types

### PublisherType Enum

```java
public enum PublisherType {
    /**
     * Automatically select the best available publisher.
     * Selection priority: KAFKA → RABBITMQ → APPLICATION_EVENT → NOOP
     */
    AUTO,
    
    /**
     * Spring Application Event Bus (in-memory).
     * Events are published synchronously within the same JVM.
     */
    APPLICATION_EVENT,
    
    /**
     * Apache Kafka.
     * High-throughput distributed streaming platform.
     */
    KAFKA,
    
    /**
     * RabbitMQ AMQP Broker.
     * Feature-rich message broker with flexible routing.
     */
    RABBITMQ,
    
    /**
     * No-operation publisher that discards all messages.
     * Used for testing or when messaging is disabled.
     */
    NOOP;
    
    /**
     * Gets a human-readable description of the publisher type.
     */
    public String getDescription();
    
    /**
     * Checks if this publisher type supports persistent messaging.
     */
    public boolean supportsPersistence();
    
    /**
     * Checks if this publisher type supports message ordering.
     */
    public boolean supportsOrdering();
    
    /**
     * Checks if this publisher type is a cloud-managed service.
     */
    public boolean isCloudService();
}
```

## Error Handling

### ErrorHandlingStrategy Enum

```java
public enum ErrorHandlingStrategy {
    /**
     * Log the error and continue processing other events.
     * The failed event is acknowledged and will not be retried.
     */
    LOG_AND_CONTINUE,
    
    /**
     * Log the error and retry the event processing.
     * The event will be retried up to the configured maximum attempts.
     */
    LOG_AND_RETRY,
    
    /**
     * Reject the event and stop processing.
     * The event will be negatively acknowledged.
     */
    REJECT_AND_STOP,
    
    /**
     * Send the failed event to a dead letter queue/topic.
     * The event will be acknowledged but forwarded to a DLQ.
     */
    DEAD_LETTER,
    
    /**
     * Ignore the error completely.
     * The event is acknowledged and processing continues.
     */
    IGNORE,
    
    /**
     * Custom error handling.
     * The error handling is delegated to a custom error handler.
     */
    CUSTOM;
    
    /**
     * Checks if this strategy includes retry logic.
     */
    public boolean includesRetry();
    
    /**
     * Checks if this strategy stops processing on error.
     */
    public boolean stopsProcessing();
    
    /**
     * Checks if this strategy acknowledges the message after error.
     */
    public boolean acknowledgesMessage();
}
```

### SerializationException

Exception thrown during serialization/deserialization operations.

```java
public class SerializationException extends RuntimeException {
    public SerializationException(String message);
    public SerializationException(String message, Throwable cause);
    public SerializationException(Throwable cause);
}
```

## Filters

### EventFilter Interface

```java
public interface EventFilter {
    /**
     * Determines whether an event should be accepted for processing.
     *
     * @param messageBody the serialized message body
     * @param headers the message headers/metadata
     * @return true if the event should be processed, false to skip it
     */
    boolean accept(String messageBody, Map<String, Object> headers);

    /**
     * Gets a description of this filter for debugging and logging purposes.
     */
    default String getDescription() {
        return this.getClass().getSimpleName();
    }

    /**
     * Tests if an event envelope matches this filter.
     */
    default boolean matches(EventEnvelope envelope) {
        String messageBody = envelope.payload() != null ? envelope.payload().toString() : "";
        return accept(messageBody, envelope.headers());
    }

    // Static factory methods
    static EventFilter byDestination(String destination);
    static EventFilter byEventType(String eventTypePattern);
    static EventFilter byHeader(String headerName, String headerValue);
    static EventFilter byHeaderPresence(String headerName);
    static EventFilter byPredicate(Predicate<EventEnvelope> predicate);
    static EventFilter and(EventFilter... filters);
    static EventFilter or(EventFilter... filters);
    static EventFilter not(EventFilter filter);
}
```

### Built-in Filter Implementations

#### DestinationEventFilter
Filters events based on destination patterns.

```java
public class DestinationEventFilter implements EventFilter {
    public DestinationEventFilter(String destinationPattern);
    public DestinationEventFilter(Pattern destinationPattern);
}
```

#### EventTypeFilter
Filters events based on event type patterns with glob support.

```java
public class EventTypeFilter implements EventFilter {
    public EventTypeFilter(String eventTypePattern);
    public EventTypeFilter(Pattern eventTypePattern);
}
```

#### HeaderEventFilter
Filters events based on header values.

```java
public class HeaderEventFilter implements EventFilter {
    public HeaderEventFilter(String headerName, String expectedValue);
    public HeaderEventFilter(String headerName, Pattern valuePattern);
}
```

#### CompositeEventFilter
Combines multiple filters with logical operations.

```java
public class CompositeEventFilter implements EventFilter {
    public enum LogicType { AND, OR }
    
    public CompositeEventFilter(LogicType logicType, EventFilter... filters);
    public CompositeEventFilter(LogicType logicType, List<EventFilter> filters);
}
```

## Metrics

### Available Metrics

The library automatically collects comprehensive metrics via Micrometer:

#### Publishing Metrics

```
# Event publication counts
firefly.eda.publish.count{publisher_type, destination, status}

# Publication duration/latency
firefly.eda.publish.duration{publisher_type, destination, event_type, status}

# Message size distribution
firefly.eda.publish.message.size{publisher_type, destination}
```

#### Consumption Metrics

```
# Event consumption counts
firefly.eda.consume.count{consumer_type, source, status}

# Processing duration
firefly.eda.consume.duration{consumer_type, source, event_type, status}
```

#### Health Metrics

```
# Publisher health status (1=healthy, 0=unhealthy)
firefly.eda.publisher.health{publisher_type, connection_id}

# Consumer health status (1=healthy, 0=unhealthy)
firefly.eda.consumer.health{consumer_type}
```

#### Resilience Metrics

```
# Circuit breaker state changes
firefly.eda.circuit.breaker.state.change{publisher_type, state}

# Retry attempts
firefly.eda.retry.attempt{publisher_type, attempt, status}

# Rate limiter rejections
firefly.eda.rate.limiter.rejection{publisher_type}
```

### EdaMetrics Class

Service for recording custom metrics.

```java
@Component
public class EdaMetrics {
    /**
     * Records a successful event publication.
     */
    public void recordPublishSuccess(String publisherType, String destination, 
                                   String eventType, Duration duration, long messageSize);

    /**
     * Records a failed event publication.
     */
    public void recordPublishFailure(String publisherType, String destination, 
                                   String eventType, Duration duration, String errorType);

    /**
     * Records a successful event consumption.
     */
    public void recordConsumeSuccess(String consumerType, String source, 
                                   String eventType, Duration processingDuration);

    /**
     * Records a failed event consumption.
     */
    public void recordConsumeFailure(String consumerType, String source, 
                                   String eventType, Duration processingDuration, String errorType);

    /**
     * Updates publisher health status.
     */
    public void updatePublisherHealth(String publisherType, String connectionId, boolean isHealthy);

    /**
     * Updates consumer health status.
     */
    public void updateConsumerHealth(String consumerType, boolean isHealthy);

    /**
     * Records circuit breaker state change.
     */
    public void recordCircuitBreakerStateChange(String publisherType, String state);

    /**
     * Records retry attempt.
     */
    public void recordRetryAttempt(String publisherType, int attempt, boolean successful);

    /**
     * Records rate limiter rejection.
     */
    public void recordRateLimiterRejection(String publisherType);
}
```

## Health Indicators

### EdaHealthIndicator

The EDA library provides a built-in health indicator component:

```java
@Component
@ConditionalOnClass(name = "org.springframework.boot.actuator.health.HealthIndicator")
@ConditionalOnProperty(prefix = "firefly.eda", name = "health-enabled", havingValue = "true", matchIfMissing = true)
public class EdaHealthIndicator {
    /**
     * Returns comprehensive health information about:
     * - EDA library configuration and availability
     * - All registered event publishers and their connection status
     * - All registered event consumers and their running status  
     * - Overall system health based on component availability
     */
    public Map<String, Object> health() {
        // Implementation returns detailed health status
    }
}
```

> **Note**: This health indicator is automatically enabled when Spring Boot Actuator is on the classpath and `firefly.eda.health-enabled=true`.

### Health Response Format

```json
{
  "status": "UP",
  "components": {
    "eda": {
      "status": "UP",
      "details": {
        "publishers": {
          "kafka": {
            "status": "UP",
            "connection": "default",
            "lastChecked": "2023-10-05T10:30:00Z",
            "details": {
              "kafka_template": "KafkaTemplate",
              "connection_id": "default"
            }
          },
          "rabbitmq": {
            "status": "DOWN",
            "connection": "default", 
            "lastChecked": "2023-10-05T10:30:00Z",
            "error": "Connection refused",
            "details": {
              "error_type": "ConnectException"
            }
          }
        },
        "consumers": {
          "kafka": {
            "status": "UP",
            "running": true,
            "lastChecked": "2023-10-05T10:30:00Z"
          }
        },
        "configuration": {
          "defaultPublisherType": "KAFKA",
          "metricsEnabled": true,
          "resilienceEnabled": true
        }
      }
    }
  }
}
```

---

This API reference provides comprehensive documentation for all public APIs in the Firefly EDA Library. For usage examples, see the [Developer Guide](DEVELOPER_GUIDE.md) and [Examples](EXAMPLES.md).