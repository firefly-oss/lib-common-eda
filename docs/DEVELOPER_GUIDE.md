# Developer Guide

This comprehensive guide covers advanced features, best practices, and detailed usage patterns for the Firefly Event Driven Architecture Library.

## Table of Contents

- [Core Concepts](#core-concepts)
- [Publishing Events](#publishing-events)
- [Consuming Events](#consuming-events)
- [Serialization](#serialization)
- [Event Filtering](#event-filtering)
- [Error Handling](#error-handling)
- [Resilience Patterns](#resilience-patterns)
- [Testing](#testing)
- [Performance](#performance)
- [Security](#security)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Core Concepts

### Event-Driven Architecture Principles

The Firefly EDA Library is built on several key principles:

1. **Reactive Programming**: All operations are non-blocking and return `Mono<T>` or `Flux<T>`
2. **Event Sourcing**: Events are the source of truth for system state
3. **Loose Coupling**: Publishers and consumers are decoupled via events
4. **Scalability**: Built for high-throughput, distributed systems
5. **Resilience**: Built-in fault tolerance and error recovery

### Event Lifecycle

```mermaid
graph TD
    A[Business Method] --> B[@PublishResult Aspect]
    B --> C[Event Serialization]
    C --> D[Publisher Selection]
    D --> E[Resilience Wrapper]
    E --> F[Platform Publisher]
    F --> G[Message Queue/Topic]
    
    G --> H[Consumer Polling]
    H --> I[Event Deserialization]
    I --> J[Event Filtering]
    J --> K[@EventListener Method]
    K --> L[Acknowledgment]
```

## Publishing Events

### @PublishResult Annotation

The `@PublishResult` annotation is the recommended way to publish events:

#### Basic Usage

```java
@PublishResult(
    publisherType = PublisherType.KAFKA,
    destination = "order-events",
    eventType = "order.created"
)
public Mono<Order> createOrder(CreateOrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

#### Advanced Features

**Conditional Publishing**:
```java
@PublishResult(
    destination = "user-events",
    eventType = "user.upgraded",
    condition = "#result.isPremium() and #result.getAge() >= 18",
    headers = {"priority=high", "source=user-service"}
)
public Mono<User> upgradeUser(String userId) {
    return userService.upgrade(userId);
}
```

**Dynamic Destinations**:
```java
@PublishResult(
    destination = "#{#result.getRegion()}-orders",
    key = "#{#result.getCustomerId()}",
    eventType = "order.regional.created"
)
public Mono<Order> createRegionalOrder(CreateOrderRequest request) {
    return orderService.create(request);
}
```

**Error Event Publishing**:
```java
@PublishResult(
    destination = "error-events",
    eventType = "order.processing.failed",
    publishOnError = true,
    async = false  // Synchronous for error events
)
public Mono<Order> processOrder(ProcessOrderRequest request) {
    return orderProcessor.process(request)
        .onErrorMap(ex -> new OrderProcessingException("Processing failed", ex));
}
```

### Direct Publisher Usage

For more control, use `EventPublisher` directly:

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EventPublisherFactory publisherFactory;

    public Mono<Void> sendNotification(String userId, String message) {
        return Mono.fromCallable(() -> createNotificationEvent(userId, message))
            .flatMap(this::publishEvent)
            .then();
    }

    private Mono<Void> publishEvent(NotificationEvent event) {
        EventPublisher publisher = publisherFactory.getPublisher(
            PublisherType.KAFKA, 
            "notifications"  // Connection ID
        );

        Map<String, Object> headers = Map.of(
            "event-type", "notification.sent",
            "user-id", event.getUserId(),
            "priority", event.getPriority().name(),
            "correlation-id", MDC.get("correlationId")
        );

        return publisher.publish(event, "notification-events", headers);
    }

    private NotificationEvent createNotificationEvent(String userId, String message) {
        return NotificationEvent.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .message(message)
            .priority(NotificationPriority.NORMAL)
            .timestamp(Instant.now())
            .build();
    }
}
```

### Batch Publishing

For high-throughput scenarios:

```java
@Service
@RequiredArgsConstructor
public class BulkEventService {

    private final EventPublisherFactory publisherFactory;

    public Flux<Void> publishBatch(List<DomainEvent> events) {
        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        
        return Flux.fromIterable(events)
            .flatMap(event -> publisher.publish(event, getDestination(event)))
            .onBackpressureBuffer(1000)  // Buffer up to 1000 events
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> publishBatchOptimized(List<DomainEvent> events) {
        // Group events by destination for optimization
        Map<String, List<DomainEvent>> grouped = events.stream()
            .collect(Collectors.groupingBy(this::getDestination));

        return Flux.fromIterable(grouped.entrySet())
            .flatMap(entry -> publishToDestination(entry.getKey(), entry.getValue()))
            .then();
    }

    private Flux<Void> publishToDestination(String destination, List<DomainEvent> events) {
        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        
        return Flux.fromIterable(events)
            .flatMap(event -> publisher.publish(event, destination))
            .onBackpressureBuffer(100);
    }
}
```

## Consuming Events

### @EventListener Annotation

#### Basic Event Handling

```java
@Component
@Slf4j
public class OrderEventHandler {

    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created"
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return processOrderCreated(event)
            .then(envelope.acknowledge())
            .doOnSuccess(v -> log.info("Processed order: {}", event.orderId()))
            .doOnError(error -> log.error("Failed to process order: {}", event.orderId(), error));
    }

    private Mono<Void> processOrderCreated(OrderCreatedEvent event) {
        return Mono.fromRunnable(() -> {
            // Business logic
            inventoryService.reserve(event.items());
            emailService.sendConfirmation(event.customerEmail());
        });
    }
}
```

#### Advanced Filtering and Routing

```java
@Component
public class SmartEventHandler {

    @EventListener(
        destinations = {"order-events", "payment-events"},
        eventTypes = {"*.created", "*.updated"},
        condition = "#envelope.headers['priority'] == 'HIGH'",
        priority = 100,
        groupId = "high-priority-processor"
    )
    public Mono<Void> handleHighPriorityEvents(EventEnvelope envelope) {
        return switch (envelope.eventType()) {
            case "order.created" -> handleOrderCreated(envelope);
            case "order.updated" -> handleOrderUpdated(envelope);
            case "payment.created" -> handlePaymentCreated(envelope);
            default -> {
                log.warn("Unknown event type: {}", envelope.eventType());
                yield envelope.acknowledge();
            }
        };
    }

    @EventListener(
        destinations = "order-events",
        eventTypes = "order.cancelled",
        condition = "#envelope.payload.amount > 1000",  // SpEL on payload
        timeoutMs = 30000  // 30-second timeout
    )
    public Mono<Void> handleLargeOrderCancellation(EventEnvelope envelope) {
        OrderCancelledEvent event = (OrderCancelledEvent) envelope.payload();
        
        return Mono.fromRunnable(() -> {
            // Special handling for large order cancellations
            fraudDetectionService.checkCancellation(event);
            managerNotificationService.notifyLargeCancellation(event);
        })
        .timeout(Duration.ofSeconds(25))  // Timeout before the annotation timeout
        .then(envelope.acknowledge());
    }
}
```

### Manual Event Consumption

For full control over consumption:

```java
@Component
@RequiredArgsConstructor
public class CustomEventConsumer {

    private final List<EventConsumer> eventConsumers;
    private final EventFilter eventFilter;

    @PostConstruct
    public void startConsuming() {
        eventConsumers.stream()
            .filter(consumer -> consumer.getConsumerType().equals("kafka"))
            .forEach(this::startConsumer);
    }

    private void startConsumer(EventConsumer consumer) {
        consumer.consume("order-events", "payment-events")
            .filter(eventFilter::matches)
            .flatMap(this::processEvent)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                result -> log.debug("Processed event successfully"),
                error -> log.error("Error processing events", error)
            );
    }

    private Mono<Void> processEvent(EventEnvelope envelope) {
        return Mono.fromRunnable(() -> {
            log.info("Processing event: {} from {}", 
                envelope.eventType(), envelope.destination());
        })
        .then(envelope.acknowledge())
        .onErrorResume(error -> {
            log.error("Failed to process event", error);
            return envelope.reject(error);
        });
    }
}
```

## Serialization

### Custom Serializers

Implement the `MessageSerializer` interface:

```java
@Component
public class AvroMessageSerializer implements MessageSerializer {

    private final Schema schema;
    private final SpecificDatumWriter<GenericRecord> datumWriter;
    private final SpecificDatumReader<GenericRecord> datumReader;

    public AvroMessageSerializer() {
        this.schema = loadSchema();
        this.datumWriter = new SpecificDatumWriter<>(schema);
        this.datumReader = new SpecificDatumReader<>(schema);
    }

    @Override
    public byte[] serialize(Object payload) throws SerializationException {
        try {
            GenericRecord record = convertToAvro(payload);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Encoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            datumWriter.write(record, encoder);
            encoder.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize to Avro", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException {
        try {
            Decoder decoder = DecoderFactory.get().binaryDecoder(data, null);
            GenericRecord record = datumReader.read(null, decoder);
            return convertFromAvro(record, targetType);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize from Avro", e);
        }
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.AVRO;
    }

    @Override
    public String getContentType() {
        return "application/avro";
    }

    @Override
    public int getPriority() {
        return 200;  // Higher than JSON
    }

    @Override
    public boolean canSerialize(Class<?> type) {
        return type.isAnnotationPresent(AvroGenerated.class);
    }

    private Schema loadSchema() {
        // Load Avro schema from resources
        return new Schema.Parser().parse(
            getClass().getResourceAsStream("/schemas/event.avsc"));
    }

    private GenericRecord convertToAvro(Object payload) {
        // Convert Java object to Avro record
        // Implementation depends on your object structure
        return null;
    }

    private <T> T convertFromAvro(GenericRecord record, Class<T> targetType) {
        // Convert Avro record to Java object
        // Implementation depends on your object structure
        return null;
    }
}
```

### Serialization Strategy

Configure serialization behavior:

```java
@Configuration
public class SerializationConfig {

    @Bean
    @Primary
    public MessageSerializer compositeSerializer(
            List<MessageSerializer> serializers,
            EdaProperties edaProperties) {
        
        return new CompositeMessageSerializer(serializers, edaProperties);
    }

    @Bean
    public MessageSerializer protobufSerializer() {
        return new ProtobufMessageSerializer();
    }

    @Bean
    public MessageSerializer customJsonSerializer() {
        return JsonMessageSerializer.builder()
            .objectMapper(customObjectMapper())
            .compressionEnabled(true)
            .build();
    }

    private ObjectMapper customObjectMapper() {
        return JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }
}
```

## Event Filtering

### Built-in Filters

```java
@Component
public class EventFilterExamples {

    public void demonstrateFilters() {
        // Simple filters
        EventFilter destinationFilter = EventFilter.byDestination("order-events");
        EventFilter typeFilter = EventFilter.byEventType("order.created");
        EventFilter headerFilter = EventFilter.byHeader("priority", "HIGH");
        
        // Complex composite filters
        EventFilter complexFilter = EventFilter.and(
            EventFilter.byDestination("order-events"),
            EventFilter.or(
                EventFilter.byEventType("order.created"),
                EventFilter.byEventType("order.updated")
            ),
            EventFilter.not(
                EventFilter.byHeader("test", "true")
            )
        );

        // Predicate-based filtering
        EventFilter customFilter = EventFilter.byPredicate(envelope -> {
            if (envelope.payload() instanceof OrderEvent orderEvent) {
                return orderEvent.amount().compareTo(BigDecimal.valueOf(100)) > 0;
            }
            return false;
        });
    }
}
```

### Custom Filters

```java
@Component
public class RegionEventFilter implements EventFilter {

    private final String currentRegion;

    public RegionEventFilter(@Value("${app.region}") String region) {
        this.currentRegion = region;
    }

    @Override
    public boolean accept(String messageBody, Map<String, Object> headers) {
        Object region = headers.get("region");
        return region != null && region.equals(currentRegion);
    }

    @Override
    public boolean matches(EventEnvelope envelope) {
        Object region = envelope.headers().get("region");
        return region != null && region.equals(currentRegion);
    }

    @Override
    public String getDescription() {
        return "Region filter for: " + currentRegion;
    }
}
```

### Filter Chains

```java
@Configuration
public class FilterConfig {

    @Bean
    public EventFilterChain defaultFilterChain(
            RegionEventFilter regionFilter,
            SecurityEventFilter securityFilter) {
        
        return EventFilterChain.builder()
            .addFilter(securityFilter, 100)      // Highest priority
            .addFilter(regionFilter, 50)
            .addFilter(EventFilter.byHeader("version", "v1"), 10)
            .build();
    }
}

@Component
@RequiredArgsConstructor
public class FilteredEventConsumer {

    private final EventFilterChain filterChain;

    @EventListener(destinations = "all-events")
    public Mono<Void> handleFilteredEvents(EventEnvelope envelope) {
        if (!filterChain.accept(envelope)) {
            return envelope.acknowledge(); // Skip filtered events
        }

        return processEvent(envelope)
            .then(envelope.acknowledge());
    }
}
```

## Error Handling

### Error Handling Strategies

```java
@Component
public class ErrorHandlingExamples {

    @EventListener(
        destinations = "critical-events",
        errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
        maxRetries = 5,
        retryDelayMs = 2000
    )
    public Mono<Void> handleCriticalEvents(EventEnvelope envelope) {
        return processCriticalEvent(envelope.payload())
            .then(envelope.acknowledge())
            .onErrorResume(NonRetryableException.class, error -> {
                log.error("Non-retryable error, sending to DLQ", error);
                return sendToDeadLetterQueue(envelope);
            });
    }

    @EventListener(
        destinations = "payment-events",
        errorStrategy = ErrorHandlingStrategy.CUSTOM
    )
    public Mono<Void> handlePaymentEventsWithCustomError(EventEnvelope envelope) {
        return processPaymentEvent(envelope.payload())
            .then(envelope.acknowledge())
            .onErrorResume(error -> customErrorHandler(envelope, error));
    }

    private Mono<Void> customErrorHandler(EventEnvelope envelope, Throwable error) {
        return switch (error) {
            case PaymentTimeoutException timeout -> {
                log.warn("Payment timeout, scheduling retry");
                yield scheduleRetry(envelope, Duration.ofMinutes(5));
            }
            case PaymentDeclinedException declined -> {
                log.info("Payment declined, notifying customer");
                yield notifyCustomerAndAck(envelope, declined);
            }
            case PaymentSystemException system -> {
                log.error("Payment system error, sending to DLQ", system);
                yield sendToDeadLetterQueue(envelope);
            }
            default -> {
                log.error("Unexpected error", error);
                yield envelope.reject(error);
            }
        };
    }
}
```

### Dead Letter Queue Handling

```java
@Component
public class DeadLetterQueueHandler {

    @EventListener(
        destinations = "payment-events-dlq",
        errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE
    )
    public Mono<Void> handleDeadLetterEvents(EventEnvelope envelope) {
        return Mono.fromRunnable(() -> {
            log.error("Processing dead letter event: {}", envelope.eventType());
            
            // Store for manual inspection
            deadLetterStorage.store(envelope);
            
            // Notify operations team
            alertingService.sendAlert(
                "Dead Letter Event",
                "Event failed processing: " + envelope.eventType()
            );
        })
        .then(envelope.acknowledge());
    }

    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void reprocessDeadLetters() {
        deadLetterStorage.getPendingEvents()
            .flatMap(this::attemptReprocessing)
            .subscribe(
                success -> log.info("Successfully reprocessed dead letter event"),
                error -> log.error("Failed to reprocess dead letter event", error)
            );
    }
}
```

## Resilience Patterns

### Circuit Breaker Configuration

```java
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerConfig orderProcessingCircuitBreaker() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(60)              // 60% failure rate
            .slowCallRateThreshold(80)             // 80% slow calls
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .minimumNumberOfCalls(10)
            .slidingWindowSize(20)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .permittedNumberOfCallsInHalfOpenState(5)
            .recordExceptions(Exception.class)
            .ignoreExceptions(ValidationException.class)
            .build();
    }

    @Bean
    public RetryConfig orderProcessingRetry() {
        return RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(500), 2.0))
            .retryOnException(ex -> !(ex instanceof ValidationException))
            .build();
    }
}
```

### Custom Resilience Wrapper

```java
@Component
@RequiredArgsConstructor
public class CustomResilientPublisher implements EventPublisher {

    private final EventPublisher delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        return Mono.fromCallable(() -> event)
            .flatMap(e -> delegate.publish(e, destination, headers))
            .transformDeferred(RateLimiterOperator.of(rateLimiter))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .doOnSuccess(v -> recordSuccess(destination))
            .doOnError(error -> recordError(destination, error));
    }

    // Implement other methods...
}
```

## Testing

### Integration Testing with TestContainers

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.eda.publishers.kafka.default.bootstrap-servers=${embedded.kafka.brokers}",
    "firefly.eda.consumer.enabled=true"
})
@Testcontainers
class EdaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest"))
        .withEmbeddedZookeeper();

    @Autowired
    private OrderService orderService;

    @Autowired
    private TestEventCollector eventCollector;

    @Test
    void shouldPublishAndConsumeOrderEvents() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId("customer-123")
            .amount(BigDecimal.valueOf(100))
            .build();

        // When
        StepVerifier.create(orderService.createOrder(request))
            .assertNext(order -> assertThat(order.getId()).isNotNull())
            .verifyComplete();

        // Then
        await().atMost(Duration.ofSeconds(5)).until(() ->
            eventCollector.getEvents().size() == 1);

        OrderCreatedEvent event = (OrderCreatedEvent) eventCollector.getEvents().get(0);
        assertThat(event.customerId()).isEqualTo("customer-123");
        assertThat(event.amount()).isEqualTo(BigDecimal.valueOf(100));
    }
}

@Component
public class TestEventCollector {
    private final List<Object> events = new CopyOnWriteArrayList<>();

    @EventListener(destinations = "order-events")
    public Mono<Void> collectEvents(EventEnvelope envelope) {
        events.add(envelope.payload());
        return envelope.acknowledge();
    }

    public List<Object> getEvents() {
        return new ArrayList<>(events);
    }

    public void clear() {
        events.clear();
    }
}
```

### Unit Testing Publishers

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private EventPublisherFactory publisherFactory;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldPublishEventWhenOrderCreated() {
        // Given
        when(publisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(any(), eq("order-events"), any()))
            .thenReturn(Mono.empty());

        Order savedOrder = Order.builder()
            .id("order-123")
            .customerId("customer-123")
            .build();
        when(orderRepository.save(any())).thenReturn(Mono.just(savedOrder));

        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId("customer-123")
            .amount(BigDecimal.valueOf(100))
            .build();

        // When
        StepVerifier.create(orderService.createOrder(request))
            .assertNext(order -> assertThat(order.getId()).isEqualTo("order-123"))
            .verifyComplete();

        // Then
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = 
            ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture(), eq("order-events"), any());
        
        OrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.orderId()).isEqualTo("order-123");
        assertThat(publishedEvent.customerId()).isEqualTo("customer-123");
    }
}
```

### Mock Event Consumers

```java
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public EventPublisher mockEventPublisher() {
        return new MockEventPublisher();
    }
}

public class MockEventPublisher implements EventPublisher {
    private final List<PublishedEvent> publishedEvents = new CopyOnWriteArrayList<>();

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        publishedEvents.add(new PublishedEvent(event, destination, headers));
        return Mono.empty();
    }

    public List<PublishedEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    public void clear() {
        publishedEvents.clear();
    }

    // Other methods...

    public record PublishedEvent(
        Object event,
        String destination,
        Map<String, Object> headers
    ) {}
}
```

## Performance

### High-Throughput Configuration

```yaml
firefly:
  eda:
    publishers:
      kafka:
        default:
          properties:
            # Producer optimizations
            batch.size: 65536
            linger.ms: 50
            compression.type: lz4
            acks: 1
            retries: 3
            buffer.memory: 134217728
    
    consumer:
      concurrency: 10
      kafka:
        default:
          properties:
            # Consumer optimizations
            fetch.min.bytes: 50000
            fetch.max.wait.ms: 500
            max.partition.fetch.bytes: 1048576

    resilience:
      rate-limiter:
        limit-for-period: 1000
        limit-refresh-period: 1s
```

### Reactive Backpressure

```java
@Component
public class HighThroughputProcessor {

    @EventListener(destinations = "high-volume-events")
    public Mono<Void> processHighVolumeEvents(EventEnvelope envelope) {
        return processEvent(envelope.payload())
            .onBackpressureBuffer(10000)  // Buffer up to 10k events
            .publishOn(Schedulers.parallel(), 1000)  // Process in parallel
            .then(envelope.acknowledge())
            .timeout(Duration.ofSeconds(30));
    }

    private Mono<Void> processEvent(Object payload) {
        return Mono.fromCallable(() -> {
            // CPU-intensive processing
            return heavyComputation(payload);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }
}
```

### Connection Pooling

```java
@Configuration
public class PerformanceConfig {

    @Bean
    public KafkaTemplate<String, Object> optimizedKafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        
        // Enable connection pooling
        template.setDefaultTopic("default-events");
        template.setProducerPerThread(true);
        
        return template;
    }

    @Bean
    public TaskExecutor eventProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("eda-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

## Security

### Event Encryption

```java
@Component
public class EncryptedEventPublisher implements EventPublisher {

    private final EventPublisher delegate;
    private final EncryptionService encryptionService;

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        return Mono.fromCallable(() -> encryptEvent(event))
            .flatMap(encryptedEvent -> delegate.publish(encryptedEvent, destination, addSecurityHeaders(headers)));
    }

    private Object encryptEvent(Object event) {
        if (event instanceof SensitiveEvent) {
            return encryptionService.encrypt(event);
        }
        return event;
    }

    private Map<String, Object> addSecurityHeaders(Map<String, Object> headers) {
        Map<String, Object> secureHeaders = new HashMap<>(headers != null ? headers : Map.of());
        secureHeaders.put("encrypted", true);
        secureHeaders.put("encryption-algorithm", "AES-256-GCM");
        return secureHeaders;
    }
}
```

### Authentication & Authorization

```java
@Component
public class SecureEventHandler {

    @EventListener(destinations = "secure-events")
    @PreAuthorize("hasRole('EVENT_PROCESSOR')")
    public Mono<Void> handleSecureEvents(EventEnvelope envelope) {
        return validateEventSource(envelope)
            .then(processSecureEvent(envelope.payload()))
            .then(envelope.acknowledge());
    }

    private Mono<Void> validateEventSource(EventEnvelope envelope) {
        String source = (String) envelope.headers().get("source-service");
        return securityService.validateSource(source)
            .switchIfEmpty(Mono.error(new UnauthorizedEventException("Invalid source")))
            .then();
    }
}
```

## Best Practices

### Event Design

1. **Immutable Events**: Use records or immutable classes
2. **Rich Context**: Include all necessary information
3. **Versioning**: Plan for event schema evolution
4. **Correlation IDs**: Include for request tracing

```java
public record OrderCreatedEvent(
    String orderId,
    String customerId,
    String correlationId,
    BigDecimal amount,
    List<OrderItem> items,
    Instant createdAt,
    String version  // For schema evolution
) implements DomainEvent {
    
    public static OrderCreatedEvent from(Order order, String correlationId) {
        return new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            correlationId,
            order.getAmount(),
            order.getItems(),
            order.getCreatedAt(),
            "1.0"
        );
    }
}
```

### Error Handling Best Practices

1. **Fail Fast**: Validate early and reject invalid events
2. **Idempotency**: Design consumers to handle duplicate events
3. **Compensation**: Implement compensating actions for failures
4. **Observability**: Log important events and errors

```java
@Component
public class IdempotentEventHandler {

    private final ProcessedEventStore processedEvents;

    @EventListener(destinations = "order-events")
    public Mono<Void> handleOrderEvent(EventEnvelope envelope) {
        String eventId = extractEventId(envelope);
        
        return processedEvents.isProcessed(eventId)
            .flatMap(processed -> {
                if (processed) {
                    log.debug("Event already processed: {}", eventId);
                    return envelope.acknowledge();
                }
                return processEvent(envelope)
                    .then(processedEvents.markProcessed(eventId))
                    .then(envelope.acknowledge());
            });
    }
}
```

### Resource Management

```java
@Component
@RequiredArgsConstructor
public class ResourceAwareEventProcessor {

    private final Semaphore processingSemaphore = new Semaphore(100);

    @EventListener(destinations = "resource-intensive-events")
    public Mono<Void> handleResourceIntensiveEvent(EventEnvelope envelope) {
        return Mono.fromCallable(() -> processingSemaphore.acquire())
            .flatMap(permit -> processEvent(envelope)
                .doFinally(signal -> processingSemaphore.release()))
            .then(envelope.acknowledge())
            .timeout(Duration.ofMinutes(5));
    }
}
```

## Troubleshooting

### Common Issues and Solutions

#### 1. Events Not Being Published

**Symptoms**: Methods with `@PublishResult` execute but no events appear in topics

**Diagnosis**:
```bash
# Check publisher health
curl http://localhost:8080/actuator/health

# Check metrics
curl http://localhost:8080/actuator/metrics/firefly.eda.publish.count
```

**Solutions**:
- Verify publisher configuration
- Check if the messaging platform is running
- Enable debug logging: `logging.level.com.firefly.common.eda=DEBUG`

#### 2. Consumer Not Processing Events

**Symptoms**: Events are published but not consumed

**Solutions**:
- Ensure `firefly.eda.consumer.enabled=true`
- Check consumer group configuration
- Verify topic/queue names match
- Check for filtering rules that might exclude events

#### 3. Performance Issues

**Symptoms**: High latency or low throughput

**Diagnosis**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics,health
  metrics:
    export:
      prometheus:
        enabled: true
```

**Solutions**:
- Tune batch sizes and timeouts
- Increase concurrency
- Enable compression
- Use connection pooling

#### 4. Memory Leaks

**Symptoms**: Increasing memory usage over time

**Solutions**:
- Enable backpressure handling
- Limit buffer sizes
- Monitor connection pools
- Use `@PreDestroy` for cleanup

```java
@Component
public class CleanupAwareComponent {

    @PreDestroy
    public void cleanup() {
        // Cleanup resources
        publisherFactory.getAllPublishers().values()
            .forEach(EventPublisher::close);
    }
}
```

---

This developer guide covers the major aspects of working with the Firefly EDA Library. For specific platform configurations, see the [Configuration Reference](CONFIGURATION.md), and for complete API documentation, see the [API Reference](API_REFERENCE.md).