# Examples

Practical examples demonstrating common usage patterns and advanced features of the Firefly Event Driven Architecture Library.

## Table of Contents

- [Basic Examples](#basic-examples)
- [E-Commerce Platform Example](#e-commerce-platform-example)
- [Banking System Example](#banking-system-example)
- [Multi-Platform Configuration](#multi-platform-configuration)
- [Testing Examples](#testing-examples)
- [Production Patterns](#production-patterns)

## Basic Examples

### Simple Order Processing

```java
// Event classes
public record OrderCreatedEvent(
    String orderId,
    String customerId,
    BigDecimal amount,
    List<String> items,
    Instant createdAt
) {}

public record PaymentProcessedEvent(
    String paymentId,
    String orderId,
    BigDecimal amount,
    String status,
    Instant processedAt
) {}

// Service with event publishing
@Service
public class OrderService {

    @PublishResult(
        destination = "order-events",
        eventType = "order.created"
    )
    public Mono<Order> createOrder(CreateOrderRequest request) {
        return Mono.just(request)
            .map(this::buildOrder)
            .flatMap(orderRepository::save)
            .doOnSuccess(order -> log.info("Order created: {}", order.getId()));
    }

    private Order buildOrder(CreateOrderRequest request) {
        return Order.builder()
            .id(UUID.randomUUID().toString())
            .customerId(request.getCustomerId())
            .amount(request.getAmount())
            .items(request.getItems())
            .status(OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    }
}

// Event listener
@Component
@Slf4j
public class OrderEventHandler {

    @EventListener(destinations = "order-events", eventTypes = "order.created")
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return Mono.fromRunnable(() -> {
            log.info("Processing new order: {}", event.orderId());
            // Business logic here
            inventoryService.reserveItems(event.items());
            emailService.sendOrderConfirmation(event.customerId(), event.orderId());
        })
        .then(envelope.acknowledge())
        .doOnError(error -> log.error("Failed to process order: {}", event.orderId(), error));
    }
}
```

## E-Commerce Platform Example

Complete e-commerce platform with multiple services and event flows.

### Domain Events

```java
// Order domain events
public record OrderCreatedEvent(
    String orderId,
    String customerId,
    BigDecimal totalAmount,
    List<OrderItem> items,
    Address shippingAddress,
    Instant createdAt,
    String correlationId
) {}

public record OrderPaidEvent(
    String orderId,
    String paymentId,
    BigDecimal amount,
    String paymentMethod,
    Instant paidAt,
    String correlationId
) {}

public record OrderShippedEvent(
    String orderId,
    String trackingNumber,
    String carrier,
    Address shippingAddress,
    Instant shippedAt,
    String correlationId
) {}

// Inventory domain events
public record InventoryReservedEvent(
    String reservationId,
    String orderId,
    List<ItemReservation> reservations,
    Instant reservedAt
) {}

public record InventoryReleasedEvent(
    String reservationId,
    String orderId,
    List<ItemReservation> reservations,
    Instant releasedAt
) {}

// Payment domain events
public record PaymentAuthorizedEvent(
    String paymentId,
    String orderId,
    BigDecimal amount,
    String customerId,
    Instant authorizedAt
) {}

public record PaymentFailedEvent(
    String paymentId,
    String orderId,
    String reason,
    Instant failedAt
) {}
```

### Order Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventPublisherFactory publisherFactory;

    @PublishResult(
        destination = "order-events",
        eventType = "order.created",
        key = "#{#result.customerId}",
        headers = {"source=order-service", "version=1.0"}
    )
    public Mono<Order> createOrder(CreateOrderRequest request) {
        String correlationId = MDC.get("correlationId");
        
        return Mono.just(request)
            .map(req -> buildOrder(req, correlationId))
            .flatMap(orderRepository::save)
            .doOnSuccess(order -> log.info("Order created: {}", order.getId()));
    }

    @EventListener(
        destinations = "payment-events",
        eventTypes = "payment.authorized",
        condition = "#envelope.payload.orderId != null"
    )
    public Mono<Void> handlePaymentAuthorized(EventEnvelope envelope) {
        PaymentAuthorizedEvent event = (PaymentAuthorizedEvent) envelope.payload();
        
        return orderRepository.findById(event.orderId())
            .flatMap(order -> updateOrderStatus(order, OrderStatus.PAID))
            .then(publishOrderPaidEvent(event))
            .then(envelope.acknowledge())
            .doOnSuccess(v -> log.info("Order payment processed: {}", event.orderId()));
    }

    @EventListener(
        destinations = "inventory-events",
        eventTypes = "inventory.released",
        errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
        maxRetries = 3
    )
    public Mono<Void> handleInventoryReleased(EventEnvelope envelope) {
        InventoryReleasedEvent event = (InventoryReleasedEvent) envelope.payload();
        
        return orderRepository.findById(event.orderId())
            .flatMap(order -> updateOrderStatus(order, OrderStatus.CANCELLED))
            .then(envelope.acknowledge());
    }

    private Order buildOrder(CreateOrderRequest request, String correlationId) {
        return Order.builder()
            .id(UUID.randomUUID().toString())
            .customerId(request.getCustomerId())
            .items(request.getItems())
            .shippingAddress(request.getShippingAddress())
            .totalAmount(calculateTotal(request.getItems()))
            .status(OrderStatus.PENDING)
            .correlationId(correlationId)
            .createdAt(Instant.now())
            .build();
    }

    private Mono<Order> updateOrderStatus(Order order, OrderStatus status) {
        return orderRepository.save(order.withStatus(status));
    }

    private Mono<Void> publishOrderPaidEvent(PaymentAuthorizedEvent paymentEvent) {
        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        
        OrderPaidEvent event = new OrderPaidEvent(
            paymentEvent.orderId(),
            paymentEvent.paymentId(),
            paymentEvent.amount(),
            "credit_card", // From payment details
            Instant.now(),
            MDC.get("correlationId")
        );

        return publisher.publish(event, "order-events", Map.of(
            "event-type", "order.paid",
            "correlation-id", event.correlationId()
        ));
    }
}
```

### Inventory Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        priority = 100  // High priority for inventory reservation
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return reserveInventory(event)
            .flatMap(this::publishInventoryReserved)
            .then(envelope.acknowledge())
            .onErrorResume(InsufficientInventoryException.class, error -> {
                return publishInventoryUnavailable(event, error)
                    .then(envelope.acknowledge());
            });
    }

    @EventListener(
        destinations = "order-events",
        eventTypes = {"order.cancelled", "order.failed"},
        timeoutMs = 30000
    )
    public Mono<Void> handleOrderCancellation(EventEnvelope envelope) {
        // Extract order ID from different event types
        String orderId = extractOrderId(envelope.payload());
        
        return reservationRepository.findByOrderId(orderId)
            .flatMap(this::releaseReservation)
            .flatMap(this::publishInventoryReleased)
            .then(envelope.acknowledge());
    }

    @PublishResult(
        destination = "inventory-events",
        eventType = "inventory.reserved",
        condition = "#result != null",
        async = true
    )
    private Mono<InventoryReservation> reserveInventory(OrderCreatedEvent event) {
        return Flux.fromIterable(event.items())
            .flatMap(item -> reserveItem(item, event.orderId()))
            .collectList()
            .map(reservations -> new InventoryReservation(
                UUID.randomUUID().toString(),
                event.orderId(),
                reservations,
                Instant.now()
            ))
            .flatMap(reservationRepository::save);
    }

    private Mono<Void> publishInventoryReserved(InventoryReservation reservation) {
        // Publishing logic
        return Mono.empty();
    }
}
```

### Payment Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentProcessor paymentProcessor;
    private final PaymentRepository paymentRepository;

    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        condition = "#envelope.payload.totalAmount > 0"
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return processPayment(event)
            .then(envelope.acknowledge())
            .onErrorResume(error -> handlePaymentError(event, error)
                .then(envelope.acknowledge()));
    }

    @PublishResult(
        destination = "payment-events",
        eventType = "payment.authorized",
        condition = "#result.status == 'AUTHORIZED'",
        publishOnError = true,
        headers = {"priority=high"}
    )
    private Mono<Payment> processPayment(OrderCreatedEvent event) {
        return paymentProcessor.authorize(
                event.customerId(),
                event.totalAmount(),
                event.orderId()
            )
            .map(authResult -> Payment.builder()
                .id(UUID.randomUUID().toString())
                .orderId(event.orderId())
                .customerId(event.customerId())
                .amount(event.totalAmount())
                .status(authResult.isSuccess() ? PaymentStatus.AUTHORIZED : PaymentStatus.FAILED)
                .processedAt(Instant.now())
                .build())
            .flatMap(paymentRepository::save);
    }

    private Mono<Void> handlePaymentError(OrderCreatedEvent event, Throwable error) {
        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
            UUID.randomUUID().toString(),
            event.orderId(),
            error.getMessage(),
            Instant.now()
        );

        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        return publisher.publish(failedEvent, "payment-events", Map.of(
            "event-type", "payment.failed",
            "error-category", classifyError(error)
        ));
    }
}
```

## Banking System Example

Banking system with strict consistency requirements and audit trails.

### Domain Events

```java
// Account events
public record AccountCreatedEvent(
    String accountId,
    String customerId,
    String accountType,
    String currency,
    Instant createdAt,
    String auditTrail
) {}

public record FundsTransferredEvent(
    String transferId,
    String fromAccountId,
    String toAccountId,
    BigDecimal amount,
    String currency,
    String reference,
    Instant transferredAt,
    String auditTrail
) {}

public record AccountFrozenEvent(
    String accountId,
    String reason,
    String authorizedBy,
    Instant frozenAt
) {}
```

### Account Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @PublishResult(
        destination = "account-events",
        eventType = "account.created",
        key = "#{#result.customerId}",
        async = false,  // Synchronous for banking
        headers = {
            "compliance-required=true",
            "audit-level=HIGH"
        }
    )
    public Mono<Account> createAccount(CreateAccountRequest request) {
        return validateCustomer(request.getCustomerId())
            .then(performKycCheck(request.getCustomerId()))
            .then(Mono.just(request)
                .map(this::buildAccount)
                .flatMap(accountRepository::save))
            .flatMap(this::recordAuditTrail);
    }

    @EventListener(
        destinations = "compliance-events",
        eventTypes = "account.flagged",
        errorStrategy = ErrorHandlingStrategy.REJECT_AND_STOP,
        priority = 1000  // Highest priority for compliance
    )
    public Mono<Void> handleAccountFlagged(EventEnvelope envelope) {
        AccountFlaggedEvent event = (AccountFlaggedEvent) envelope.payload();
        
        return accountRepository.findById(event.accountId())
            .flatMap(account -> freezeAccount(account, event.reason()))
            .then(notifyComplianceTeam(event))
            .then(envelope.acknowledge());
    }

    @PublishResult(
        destination = "account-events",
        eventType = "account.frozen",
        async = false,
        publishOnError = true
    )
    private Mono<Account> freezeAccount(Account account, String reason) {
        return accountRepository.save(account.withStatus(AccountStatus.FROZEN))
            .flatMap(frozenAccount -> auditService.recordAccountAction(
                frozenAccount.getId(),
                "FREEZE",
                reason,
                getCurrentUser()
            ).thenReturn(frozenAccount));
    }
}
```

### Transfer Service with Saga Pattern

```java
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final SagaManager sagaManager;

    @PublishResult(
        destination = "transfer-events",
        eventType = "transfer.initiated",
        condition = "#result.status == 'INITIATED'",
        async = false
    )
    public Mono<Transfer> initiateTransfer(TransferRequest request) {
        return validateTransfer(request)
            .then(createTransfer(request))
            .flatMap(transfer -> sagaManager.startSaga(
                "money-transfer",
                transfer.getId(),
                Map.of("transfer", transfer)
            ).thenReturn(transfer));
    }

    @EventListener(
        destinations = "transfer-events",
        eventTypes = "transfer.initiated"
    )
    public Mono<Void> handleTransferInitiated(EventEnvelope envelope) {
        TransferInitiatedEvent event = (TransferInitiatedEvent) envelope.payload();
        
        return debitSourceAccount(event)
            .then(envelope.acknowledge())
            .onErrorResume(error -> {
                return publishTransferFailed(event, error)
                    .then(envelope.acknowledge());
            });
    }

    @EventListener(
        destinations = "account-events",
        eventTypes = "account.debited",
        condition = "#envelope.headers['saga-id'] != null"
    )
    public Mono<Void> handleAccountDebited(EventEnvelope envelope) {
        AccountDebitedEvent event = (AccountDebitedEvent) envelope.payload();
        String sagaId = (String) envelope.headers().get("saga-id");
        
        return creditDestinationAccount(event)
            .then(sagaManager.advanceSaga(sagaId, "account-debited"))
            .then(envelope.acknowledge());
    }

    @PublishResult(
        destination = "account-events",
        eventType = "account.debit.requested",
        headers = {"saga-id=#{#sagaId}"}
    )
    private Mono<AccountDebitRequest> debitSourceAccount(TransferInitiatedEvent event) {
        return Mono.just(new AccountDebitRequest(
            event.fromAccountId(),
            event.amount(),
            event.transferId(),
            "Transfer to " + event.toAccountId()
        ));
    }
}
```

## Multi-Platform Configuration

### Configuration for Multiple Environments

```yaml
# application.yml - Base configuration
firefly:
  eda:
    enabled: true
    default-publisher-type: AUTO
    metrics-enabled: true
    health-enabled: true
    
    resilience:
      enabled: true
      circuit-breaker:
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      retry:
        max-attempts: 3
        wait-duration: 1s

---
# application-development.yml - Development environment
spring:
  config:
    activate:
      on-profile: development

firefly:
  eda:
    default-publisher-type: APPLICATION_EVENT
    publishers:
      application-event:
        enabled: true
        default-destination: dev-events
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          default-topic: dev-events

---
# application-staging.yml - Staging environment
spring:
  config:
    activate:
      on-profile: staging

firefly:
  eda:
    default-publisher-type: KAFKA
    publishers:
      kafka:
        default:
          enabled: true
          bootstrap-servers: ${KAFKA_BROKERS:staging-kafka:9092}
          default-topic: staging-events
          properties:
            acks: all
            retries: 3
    consumer:
      enabled: true
      group-id: ${spring.application.name}-staging
      
---
# application-production.yml - Production environment
spring:
  config:
    activate:
      on-profile: production

firefly:
  eda:
    default-publisher-type: KAFKA
    publishers:
      kafka:
        primary:
          enabled: true
          bootstrap-servers: ${KAFKA_PRIMARY_BROKERS}
          default-topic: production-events
          properties:
            acks: all
            retries: 5
            batch.size: 65536
            linger.ms: 50
            compression.type: lz4
        secondary:
          enabled: true
          bootstrap-servers: ${KAFKA_SECONDARY_BROKERS}
          default-topic: production-events-backup
      
          
    consumer:
      enabled: true
      group-id: ${spring.application.name}
      concurrency: 10
      
    resilience:
      circuit-breaker:
        failure-rate-threshold: 50
        minimum-number-of-calls: 20
      rate-limiter:
        enabled: true
        limit-for-period: 1000
```

### Multi-Platform Publisher Configuration

```java
@Configuration
public class MultiPlatformConfig {

    @Bean
    @Primary
    public EventPublisher hybridEventPublisher(
            EventPublisherFactory publisherFactory,
            EdaProperties edaProperties) {
        
        return new HybridEventPublisher(publisherFactory, edaProperties);
    }
}

@Component
@RequiredArgsConstructor
public class HybridEventPublisher implements EventPublisher {

    private final EventPublisherFactory publisherFactory;
    private final EdaProperties edaProperties;

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        // Route based on event characteristics
        PublisherType publisherType = selectPublisher(event, destination, headers);
        
        EventPublisher publisher = publisherFactory.getPublisher(publisherType);
        return publisher.publish(event, destination, headers)
            .onErrorResume(error -> fallbackPublish(event, destination, headers, publisherType));
    }

    private PublisherType selectPublisher(Object event, String destination, Map<String, Object> headers) {
        // High-priority events go to Kafka
        if (isHighPriority(headers)) {
            return PublisherType.KAFKA;
        }
        
        // Audit events require guaranteed delivery
        if (isAuditEvent(event)) {
            return PublisherType.RABBITMQ;
        }
        
        // Large events or internal processing go to Spring Events for simple cases
        if (isLargeEvent(event) || isInternalEvent(event)) {
            return PublisherType.APPLICATION_EVENT;
        }
        
        return PublisherType.AUTO;
    }

    private Mono<Void> fallbackPublish(Object event, String destination, 
                                     Map<String, Object> headers, PublisherType failedPublisher) {
        // Try alternative publishers in order of preference
        PublisherType[] fallbacks = getFallbackPublishers(failedPublisher);
        
        return Flux.fromArray(fallbacks)
            .flatMap(type -> {
                EventPublisher publisher = publisherFactory.getPublisher(type);
                if (publisher != null && publisher.isAvailable()) {
                    return publisher.publish(event, destination, headers);
                }
                return Mono.empty();
            })
            .next()
            .switchIfEmpty(Mono.error(new RuntimeException("All publishers failed")));
    }
}
```

## Testing Examples

### Integration Testing with TestContainers

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.eda.publishers.kafka.default.bootstrap-servers=${embedded.kafka.brokers}",
    "firefly.eda.consumer.enabled=true",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Testcontainers
class ECommerceIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest"))
        .withEmbeddedZookeeper();

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("embedded.kafka.brokers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private TestEventCollector eventCollector;

    @Test
    void shouldProcessCompleteOrderFlow() {
        // Given
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
            .customerId("customer-123")
            .items(List.of(
                new OrderItem("product-1", 2, new BigDecimal("50.00")),
                new OrderItem("product-2", 1, new BigDecimal("30.00"))
            ))
            .totalAmount(new BigDecimal("130.00"))
            .build();

        // When - Create order
        StepVerifier.create(orderService.createOrder(orderRequest))
            .assertNext(order -> {
                assertThat(order.getId()).isNotNull();
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("130.00"));
            })
            .verifyComplete();

        // Then - Verify order created event
        await().atMost(Duration.ofSeconds(10)).until(() ->
            eventCollector.getEventsOfType(OrderCreatedEvent.class).size() == 1);

        OrderCreatedEvent orderEvent = eventCollector.getEventsOfType(OrderCreatedEvent.class).get(0);
        assertThat(orderEvent.correlationId()).isEqualTo(correlationId);
        assertThat(orderEvent.totalAmount()).isEqualTo(new BigDecimal("130.00"));

        // When - Payment processes the order
        await().atMost(Duration.ofSeconds(10)).until(() ->
            eventCollector.getEventsOfType(PaymentAuthorizedEvent.class).size() == 1);

        PaymentAuthorizedEvent paymentEvent = eventCollector.getEventsOfType(PaymentAuthorizedEvent.class).get(0);
        assertThat(paymentEvent.orderId()).isEqualTo(orderEvent.orderId());

        // Then - Order should be marked as paid
        await().atMost(Duration.ofSeconds(10)).until(() ->
            eventCollector.getEventsOfType(OrderPaidEvent.class).size() == 1);

        OrderPaidEvent paidEvent = eventCollector.getEventsOfType(OrderPaidEvent.class).get(0);
        assertThat(paidEvent.orderId()).isEqualTo(orderEvent.orderId());
        assertThat(paidEvent.paymentId()).isEqualTo(paymentEvent.paymentId());
    }

    @Test
    void shouldHandlePaymentFailure() {
        // Given - Configure payment to fail
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
            .customerId("customer-invalid")  // This will cause payment to fail
            .items(List.of(new OrderItem("product-1", 1, new BigDecimal("100.00"))))
            .totalAmount(new BigDecimal("100.00"))
            .build();

        // When
        StepVerifier.create(orderService.createOrder(orderRequest))
            .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING))
            .verifyComplete();

        // Then - Should receive payment failed event
        await().atMost(Duration.ofSeconds(10)).until(() ->
            eventCollector.getEventsOfType(PaymentFailedEvent.class).size() == 1);

        PaymentFailedEvent failedEvent = eventCollector.getEventsOfType(PaymentFailedEvent.class).get(0);
        assertThat(failedEvent.reason()).contains("Invalid customer");
    }
}

@TestComponent
@Slf4j
public class TestEventCollector {

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<Object>> eventsByType = new ConcurrentHashMap<>();

    @EventListener(destinations = {"order-events", "payment-events", "inventory-events"})
    public Mono<Void> collectEvents(EventEnvelope envelope) {
        Object event = envelope.payload();
        events.add(event);
        eventsByType.computeIfAbsent(event.getClass(), k -> new CopyOnWriteArrayList<>()).add(event);
        
        log.debug("Collected event: {} - {}", event.getClass().getSimpleName(), event);
        return envelope.acknowledge();
    }

    public List<Object> getAllEvents() {
        return new ArrayList<>(events);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getEventsOfType(Class<T> eventType) {
        return (List<T>) eventsByType.getOrDefault(eventType, List.of());
    }

    public void clear() {
        events.clear();
        eventsByType.clear();
    }

    public int getEventCount() {
        return events.size();
    }
}
```

### Unit Testing with Mocks

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private EventPublisherFactory publisherFactory;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @Mock
    private AuditService auditService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        when(publisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(any(), any(), any())).thenReturn(Mono.empty());
        when(auditService.recordOrderAction(any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId("customer-123")
            .items(List.of(new OrderItem("product-1", 2, new BigDecimal("25.00"))))
            .build();

        Order savedOrder = Order.builder()
            .id("order-123")
            .customerId("customer-123")
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("50.00"))
            .createdAt(Instant.now())
            .build();

        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(savedOrder));

        // When
        StepVerifier.create(orderService.createOrder(request))
            .assertNext(order -> {
                assertThat(order.getId()).isEqualTo("order-123");
                assertThat(order.getCustomerId()).isEqualTo("customer-123");
                assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("50.00"));
            })
            .verifyComplete();

        // Then - Verify event was published
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture(), eq("order-events"), any());
        
        OrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.orderId()).isEqualTo("order-123");
        assertThat(publishedEvent.customerId()).isEqualTo("customer-123");
        assertThat(publishedEvent.totalAmount()).isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void shouldHandlePaymentAuthorizedEvent() {
        // Given
        PaymentAuthorizedEvent event = new PaymentAuthorizedEvent(
            "payment-123",
            "order-123",
            new BigDecimal("50.00"),
            "customer-123",
            Instant.now()
        );

        Order existingOrder = Order.builder()
            .id("order-123")
            .customerId("customer-123")
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("50.00"))
            .build();

        Order updatedOrder = existingOrder.withStatus(OrderStatus.PAID);

        EventEnvelope envelope = new EventEnvelope(
            "payment-events",
            "payment.authorized",
            event,
            UUID.randomUUID().toString(),
            Map.of(),
            Instant.now(),
            "test-consumer",
            mock(EventEnvelope.AckCallback.class)
        );

        when(orderRepository.findById("order-123")).thenReturn(Mono.just(existingOrder));
        when(orderRepository.save(updatedOrder)).thenReturn(Mono.just(updatedOrder));
        when(envelope.ackCallback().acknowledge()).thenReturn(Mono.empty());

        // When
        StepVerifier.create(orderService.handlePaymentAuthorized(envelope))
            .verifyComplete();

        // Then
        verify(orderRepository).findById("order-123");
        verify(orderRepository).save(updatedOrder);
        
        // Verify OrderPaidEvent was published
        ArgumentCaptor<OrderPaidEvent> eventCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture(), eq("order-events"), any());
        
        OrderPaidEvent paidEvent = eventCaptor.getValue();
        assertThat(paidEvent.orderId()).isEqualTo("order-123");
        assertThat(paidEvent.paymentId()).isEqualTo("payment-123");
    }
}
```

## Production Patterns

### Circuit Breaker and Resilience

```java
@Configuration
public class ProductionResilienceConfig {

    @Bean
    public EventPublisher resilientOrderPublisher(
            @Qualifier("kafkaEventPublisher") EventPublisher kafkaPublisher,
            @Qualifier("sqsEventPublisher") EventPublisher sqsPublisher,
            CircuitBreakerFactory circuitBreakerFactory,
            RetryTemplate retryTemplate) {
        
        return new MultiTierResilientPublisher(
            kafkaPublisher,
            sqsPublisher,
            circuitBreakerFactory,
            retryTemplate
        );
    }
}

@Component
@RequiredArgsConstructor
public class MultiTierResilientPublisher implements EventPublisher {

    private final EventPublisher primaryPublisher;
    private final EventPublisher fallbackPublisher;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final RetryTemplate retryTemplate;

    private final CircuitBreaker circuitBreaker;

    @PostConstruct
    public void init() {
        this.circuitBreaker = circuitBreakerFactory.create("order-publisher");
    }

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        return Mono.fromCallable(() -> retryTemplate.execute(context -> {
                return circuitBreaker.executeSupplier(() -> 
                    primaryPublisher.publish(event, destination, headers).block());
            }))
            .onErrorResume(CircuitBreakerOpenException.class, error -> {
                log.warn("Circuit breaker open, using fallback publisher");
                return fallbackPublisher.publish(event, destination, addFallbackHeaders(headers));
            })
            .onErrorResume(error -> {
                log.error("All publishers failed, storing for retry", error);
                return storeForRetry(event, destination, headers);
            });
    }

    private Map<String, Object> addFallbackHeaders(Map<String, Object> headers) {
        Map<String, Object> newHeaders = new HashMap<>(headers != null ? headers : Map.of());
        newHeaders.put("fallback-used", true);
        newHeaders.put("fallback-timestamp", Instant.now().toString());
        return newHeaders;
    }

    private Mono<Void> storeForRetry(Object event, String destination, Map<String, Object> headers) {
        // Store event for later retry when publishers recover
        return deadLetterQueueService.store(event, destination, headers);
    }
}
```

### High-Throughput Batch Processing

```java
@Component
@RequiredArgsConstructor
public class HighVolumeEventProcessor {

    private final EventPublisherFactory publisherFactory;
    private final BatchProcessor batchProcessor;

    @EventListener(
        destinations = "high-volume-events",
        concurrency = 20  // Process in parallel
    )
    public Mono<Void> processHighVolumeEvents(EventEnvelope envelope) {
        return Mono.just(envelope)
            .publishOn(Schedulers.parallel())
            .flatMap(this::processEvent)
            .onBackpressureBuffer(10000, BufferOverflowStrategy.DROP_LATEST)
            .timeout(Duration.ofSeconds(30))
            .then(envelope.acknowledge())
            .onErrorResume(error -> handleProcessingError(envelope, error));
    }

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void processBatch() {
        batchProcessor.processAccumulatedEvents()
            .subscribe(
                count -> log.info("Processed {} events in batch", count),
                error -> log.error("Batch processing failed", error)
            );
    }

    private Mono<Void> processEvent(EventEnvelope envelope) {
        return Mono.fromCallable(() -> {
            // CPU-intensive processing
            Object processedResult = performComplexCalculation(envelope.payload());
            
            // Add to batch for efficient publishing
            batchProcessor.addToBatch(processedResult, envelope.destination());
            
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> handleProcessingError(EventEnvelope envelope, Throwable error) {
        if (isRetryableError(error)) {
            return scheduleRetry(envelope, Duration.ofMinutes(1));
        } else {
            return envelope.reject(error);
        }
    }
}

@Component
@RequiredArgsConstructor
public class BatchProcessor {

    private final EventPublisherFactory publisherFactory;
    private final Map<String, List<Object>> batchBuffer = new ConcurrentHashMap<>();
    private final AtomicInteger batchSize = new AtomicInteger(0);

    public void addToBatch(Object event, String destination) {
        batchBuffer.computeIfAbsent(destination, k -> new CopyOnWriteArrayList<>()).add(event);
        int currentSize = batchSize.incrementAndGet();
        
        // Auto-flush when batch reaches threshold
        if (currentSize >= 1000) {
            processAccumulatedEvents().subscribe();
        }
    }

    public Mono<Integer> processAccumulatedEvents() {
        Map<String, List<Object>> currentBatch = new HashMap<>(batchBuffer);
        batchBuffer.clear();
        batchSize.set(0);

        return Flux.fromIterable(currentBatch.entrySet())
            .flatMap(entry -> publishBatch(entry.getKey(), entry.getValue()))
            .reduce(0, Integer::sum);
    }

    private Mono<Integer> publishBatch(String destination, List<Object> events) {
        if (events.isEmpty()) {
            return Mono.just(0);
        }

        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        
        return Flux.fromIterable(events)
            .flatMap(event -> publisher.publish(event, destination)
                .onErrorResume(error -> {
                    log.error("Failed to publish event in batch", error);
                    return Mono.empty();
                }))
            .count()
            .map(Math::toIntExact);
    }
}
```

### Monitoring and Alerting Integration

```java
@Component
@RequiredArgsConstructor
public class ProductionEventMonitor {

    private final MeterRegistry meterRegistry;
    private final AlertingService alertingService;

    @EventListener(
        destinations = "*",  // Monitor all destinations
        priority = Integer.MAX_VALUE  // Highest priority
    )
    public Mono<Void> monitorAllEvents(EventEnvelope envelope) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return Mono.fromRunnable(() -> {
            // Update metrics
            meterRegistry.counter("events.processed",
                "destination", envelope.destination(),
                "type", envelope.eventType(),
                "consumer", envelope.consumerType()
            ).increment();
            
            // Check for anomalies
            checkForAnomalies(envelope);
        })
        .then(envelope.acknowledge())
        .doFinally(signalType -> {
            sample.stop(Timer.builder("event.processing.duration")
                .tag("destination", envelope.destination())
                .tag("type", envelope.eventType())
                .tag("status", signalType.name())
                .register(meterRegistry));
        })
        .onErrorResume(error -> {
            // Record error metrics
            meterRegistry.counter("events.errors",
                "destination", envelope.destination(),
                "type", envelope.eventType(),
                "error", error.getClass().getSimpleName()
            ).increment();
            
            // Send alert for critical errors
            if (isCriticalError(error)) {
                alertingService.sendAlert(
                    AlertLevel.CRITICAL,
                    "Critical event processing error",
                    Map.of(
                        "destination", envelope.destination(),
                        "eventType", envelope.eventType(),
                        "error", error.getMessage(),
                        "timestamp", Instant.now()
                    )
                ).subscribe();
            }
            
            return envelope.reject(error);
        });
    }

    private void checkForAnomalies(EventEnvelope envelope) {
        // Check processing time
        Duration processingTime = Duration.between(envelope.timestamp(), Instant.now());
        if (processingTime.toMillis() > 10000) { // > 10 seconds
            alertingService.sendAlert(
                AlertLevel.WARNING,
                "Slow event processing detected",
                Map.of(
                    "destination", envelope.destination(),
                    "eventType", envelope.eventType(),
                    "processingTime", processingTime.toString()
                )
            ).subscribe();
        }
        
        // Check for suspicious patterns
        if (envelope.eventType().contains("suspicious") || 
            envelope.headers().containsKey("fraud-alert")) {
            alertingService.sendAlert(
                AlertLevel.HIGH,
                "Suspicious event detected",
                Map.of(
                    "eventType", envelope.eventType(),
                    "headers", envelope.headers()
                )
            ).subscribe();
        }
    }

    private boolean isCriticalError(Throwable error) {
        return error instanceof SecurityException ||
               error instanceof DataIntegrityViolationException ||
               error.getMessage().contains("CRITICAL");
    }
}
```

---

These examples demonstrate real-world usage patterns and best practices for the Firefly EDA Library, covering everything from basic event handling to complex production scenarios with resilience, monitoring, and multi-platform support.