# Quick Start Guide

This guide will help you get started with the Firefly Event Driven Architecture Library in under 15 minutes.

---

## ⚠️ IMPORTANT - Configuration Principle

**This library follows hexagonal architecture.**

✅ **ALWAYS configure using `firefly.eda.*` properties**
❌ **NEVER use `spring.kafka.*` or `spring.rabbitmq.*` properties**

The library manages all provider-specific configurations internally, ensuring complete abstraction from messaging platforms.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Step 1: Add Dependency](#step-1-add-dependency)
- [Step 2: Configure Messaging](#step-2-configure-messaging)
- [Step 3: Publishing Events](#step-3-publishing-events)
- [Step 4: Consuming Events](#step-4-consuming-events)
- [Step 5: Running Your Application](#step-5-running-your-application)
- [Testing Your Setup](#testing-your-setup)
- [Next Steps](#next-steps)

## Prerequisites

- **Java 21+**: The library requires Java 21 or higher
- **Spring Boot 3.2+**: Compatible with Spring Boot 3.2 and above
- **Messaging Platform**: One of the following (for development, you can use Docker):
  - Apache Kafka
  - RabbitMQ
  - Or use Spring Application Events (no external dependencies)

## Step 1: Add Dependency

Add the Firefly EDA library to your `pom.xml`:

```xml
<dependencies>
    <!-- Firefly EDA Library -->
    <dependency>
        <groupId>com.firefly</groupId>
        <artifactId>lib-common-eda</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- For web applications (optional) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- For health checks and metrics (optional) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

## Step 2: Configure Messaging

### Option A: Spring Application Events (No External Dependencies)

Perfect for getting started quickly or single-instance applications:

```yaml
# application.yml
firefly:
  eda:
    enabled: true
    default-publisher-type: APPLICATION_EVENT
    publishers:
      application-event:
        enabled: true
        default-destination: app-events
```

### Option B: Apache Kafka (Recommended for Production)

First, start Kafka using Docker:

```bash
# docker-compose.yml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

# Start services
docker-compose up -d
```

Then configure your application:

```yaml
# application.yml
firefly:
  eda:
    enabled: true
    default-publisher-type: KAFKA
    publishers:
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092  # ✅ CORRECT - firefly.eda namespace
          default-topic: events
    consumer:
      enabled: true
      group-id: my-app
      kafka:
        default:
          bootstrap-servers: localhost:9092  # ✅ CORRECT - firefly.eda namespace
          topics: events

# ❌ DO NOT USE - These will be IGNORED:
# spring:
#   kafka:
#     bootstrap-servers: localhost:9092  # ❌ IGNORED!
```

### Option C: RabbitMQ

Start RabbitMQ:

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

Configure:

```yaml
firefly:
  eda:
    enabled: true
    default-publisher-type: RABBITMQ
    publishers:
      rabbitmq:
        default:
          enabled: true
          host: localhost  # ✅ CORRECT - firefly.eda namespace
          port: 5672
          username: guest
          password: guest
          default-exchange: events

# ❌ DO NOT USE - These will be IGNORED:
# spring:
#   rabbitmq:
#     host: localhost  # ❌ IGNORED!
#     port: 5672       # ❌ IGNORED!
```

## Step 3: Publishing Events

### Create an Event Class

```java
// OrderEvent.java
public record OrderEvent(
    String orderId,
    String customerId,
    String status,
    BigDecimal amount,
    Instant timestamp
) {}
```

### Method 1: Using @PublishResult Annotation (Recommended)

```java
// OrderService.java
@Service
public class OrderService {

    @PublishResult(
        publisherType = PublisherType.AUTO,  // Will auto-select best publisher
        destination = "order-events",
        eventType = "order.created"
    )
    public Mono<Order> createOrder(CreateOrderRequest request) {
        return Mono.just(request)
            .map(this::processRequest)
            .map(this::saveOrder)
            .doOnSuccess(order -> 
                log.info("Order created: {}", order.getId()));
    }
    
    @PublishResult(
        destination = "order-events",
        eventType = "order.updated",
        condition = "#result.status == 'COMPLETED'"  // Only publish when completed
    )
    public Mono<Order> updateOrderStatus(String orderId, OrderStatus status) {
        return orderRepository.findById(orderId)
            .map(order -> order.withStatus(status))
            .flatMap(orderRepository::save);
    }

    private Order processRequest(CreateOrderRequest request) {
        return Order.builder()
            .id(UUID.randomUUID().toString())
            .customerId(request.getCustomerId())
            .amount(request.getAmount())
            .status(OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    }

    private Order saveOrder(Order order) {
        // Simulate saving to database
        return order;
    }
}
```

### Method 2: Using EventPublisher Directly

```java
// PaymentService.java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final EventPublisherFactory publisherFactory;

    public Mono<Payment> processPayment(PaymentRequest request) {
        return Mono.just(request)
            .map(this::createPayment)
            .flatMap(this::savePayment)
            .flatMap(this::publishPaymentEvent);
    }

    private Mono<Payment> publishPaymentEvent(Payment payment) {
        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        
        PaymentEvent event = new PaymentEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getStatus().name(),
            payment.getProcessedAt()
        );

        Map<String, Object> headers = Map.of(
            "event-type", "payment.processed",
            "transaction-id", payment.getTransactionId(),
            "correlation-id", payment.getOrderId()
        );

        return publisher.publish(event, "payment-events", headers)
            .thenReturn(payment);
    }

    private Payment createPayment(PaymentRequest request) {
        return Payment.builder()
            .id(UUID.randomUUID().toString())
            .orderId(request.getOrderId())
            .amount(request.getAmount())
            .status(PaymentStatus.PENDING)
            .transactionId(UUID.randomUUID().toString())
            .processedAt(Instant.now())
            .build();
    }

    private Mono<Payment> savePayment(Payment payment) {
        // Simulate async database save
        return Mono.just(payment.withStatus(PaymentStatus.COMPLETED));
    }
}
```

## Step 4: Consuming Events

### Basic Event Listener

```java
// OrderEventHandler.java
@Component
@Slf4j
public class OrderEventHandler {

    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created"
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        return Mono.fromRunnable(() -> {
            OrderEvent event = (OrderEvent) envelope.payload();
            log.info("Processing new order: {} for customer: {}", 
                event.orderId(), event.customerId());
            
            // Process the order...
            processNewOrder(event);
        })
        .then(envelope.acknowledge())
        .doOnError(error -> log.error("Failed to process order created event", error));
    }

    @EventListener(
        destinations = "order-events", 
        eventTypes = {"order.updated", "order.cancelled"},
        priority = 100  // Higher priority
    )
    public Mono<Void> handleOrderStatusChanges(EventEnvelope envelope) {
        return Mono.fromRunnable(() -> {
            OrderEvent event = (OrderEvent) envelope.payload();
            log.info("Order status changed: {} -> {}", 
                event.orderId(), event.status());
            
            // Update related systems...
            updateInventory(event);
            notifyCustomer(event);
        })
        .then(envelope.acknowledge());
    }

    private void processNewOrder(OrderEvent event) {
        // Business logic for new orders
    }

    private void updateInventory(OrderEvent event) {
        // Update inventory based on order status
    }

    private void notifyCustomer(OrderEvent event) {
        // Send notification to customer
    }
}
```

### Advanced Event Processing with Error Handling

```java
// PaymentEventHandler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventHandler {

    private final NotificationService notificationService;
    private final InventoryService inventoryService;

    @EventListener(
        destinations = "payment-events",
        eventTypes = "payment.processed",
        errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
        maxRetries = 3,
        retryDelayMs = 1000
    )
    public Mono<Void> handlePaymentProcessed(EventEnvelope envelope) {
        PaymentEvent event = (PaymentEvent) envelope.payload();
        
        return processPaymentCompleted(event)
            .then(envelope.acknowledge())
            .onErrorResume(error -> {
                log.error("Failed to process payment event: {}", event.paymentId(), error);
                return envelope.reject(error);
            });
    }

    @EventListener(
        destinations = "payment-events",
        eventTypes = "payment.failed",
        errorStrategy = ErrorHandlingStrategy.DEAD_LETTER
    )
    public Mono<Void> handlePaymentFailed(EventEnvelope envelope) {
        PaymentEvent event = (PaymentEvent) envelope.payload();
        
        return Mono.fromRunnable(() -> 
            log.warn("Payment failed for order: {}", event.orderId())
        )
        .then(notificationService.notifyPaymentFailure(event.orderId()))
        .then(envelope.acknowledge())
        .timeout(Duration.ofSeconds(30));
    }

    private Mono<Void> processPaymentCompleted(PaymentEvent event) {
        return inventoryService.reserveItems(event.orderId())
            .then(notificationService.notifyPaymentSuccess(event.orderId()));
    }
}
```

## Step 5: Running Your Application

### Create Your Main Application Class

```java
// Application.java
@SpringBootApplication
@Slf4j
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    // Optional: Custom configuration
    @Bean
    public RouterFunction<ServerResponse> routes(OrderService orderService) {
        return RouterFunctions.route()
            .POST("/orders", request -> 
                request.bodyToMono(CreateOrderRequest.class)
                    .flatMap(orderService::createOrder)
                    .flatMap(order -> ServerResponse.ok().bodyValue(order))
            )
            .build();
    }
}
```

### Run the Application

```bash
# Using Maven
mvn spring-boot:run

# Using Gradle
./gradlew bootRun

# Or run the jar directly
java -jar target/my-app.jar
```

## Testing Your Setup

### 1. Check Health Status

If you have Spring Boot Actuator enabled:

```bash
curl http://localhost:8080/actuator/health
```

The EDA health indicator provides detailed information:

```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "defaultPublisherType": "KAFKA",
    "publishers": {
      "kafka": {
        "status": "UP",
        "available": true,
        "publisherType": "KAFKA",
        "connectionId": "default"
      }
    },
    "resilience": {
      "enabled": true,
      "circuitBreakerEnabled": true
    }
  }
}
```

### 2. Test Event Publishing

Create a test endpoint to trigger events:

```java
@RestController
@RequiredArgsConstructor
public class TestController {

    private final OrderService orderService;

    @PostMapping("/test/order")
    public Mono<String> createTestOrder() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId("customer-123")
            .amount(new BigDecimal("99.99"))
            .items(List.of("item1", "item2"))
            .build();

        return orderService.createOrder(request)
            .map(order -> "Order created: " + order.getId());
    }
}
```

Test it:

```bash
curl -X POST http://localhost:8080/test/order
```

### 3. Monitor Metrics

View metrics at:

```bash
curl http://localhost:8080/actuator/metrics/firefly.eda.publish.count
curl http://localhost:8080/actuator/metrics/firefly.eda.consume.count
```

### 4. Check Application Logs

Look for log messages like:

```
INFO  - Successfully published event with resilience: destination=order-events, type=OrderEvent
INFO  - Processing new order: order-123 for customer: customer-123
```

## Next Steps

Now that you have a basic setup running:

1. **Explore Advanced Features**:
   - [Resilience Patterns](DEVELOPER_GUIDE.md#resilience)
   - [Custom Serializers](DEVELOPER_GUIDE.md#serialization)
   - [Event Filtering](DEVELOPER_GUIDE.md#filtering)

2. **Production Configuration**:
   - [Security Setup](CONFIGURATION.md#security)
   - [Performance Tuning](CONFIGURATION.md#performance)
   - [Monitoring Setup](CONFIGURATION.md#monitoring)

3. **Testing**:
   - [Integration Testing](DEVELOPER_GUIDE.md#testing)
   - [TestContainers Setup](EXAMPLES.md#testcontainers)

4. **Multiple Publishers**:
   - [Multi-Platform Setup](CONFIGURATION.md#multi-platform)
   - [Failover Configuration](CONFIGURATION.md#failover)

## Troubleshooting

### Common Issues

**1. Auto-configuration not working**

Make sure you have the correct Spring Boot version and the library is in your classpath:

```bash
mvn dependency:tree | grep lib-common-eda
```

**2. Publishers not available**

Check your configuration and ensure the messaging platform is running:

```bash
# For Kafka
docker ps | grep kafka

# Check application logs
tail -f logs/application.log | grep EDA
```

**3. Events not being consumed**

Verify your consumer configuration:

```yaml
firefly:
  eda:
    consumer:
      enabled: true  # Make sure this is true
      group-id: your-app
```

**4. Connection issues**

Test your messaging platform connectivity:

```bash
# For Kafka
kafka-topics --bootstrap-server localhost:9092 --list

# For RabbitMQ
curl -u guest:guest http://localhost:15672/api/overview
```

### Getting Help

- Check the [Developer Guide](DEVELOPER_GUIDE.md) for detailed documentation
- Review [Examples](EXAMPLES.md) for common use cases
- Look at the [API Reference](API_REFERENCE.md) for specific method documentation

---

Congratulations! 🎉 You now have a working event-driven application with the Firefly EDA Library. The next step is to explore the advanced features and production configurations in our detailed documentation.