# Firefly Event Driven Architecture Library

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-green.svg)](https://spring.io/projects/spring-boot)
[![Firefly Platform](https://img.shields.io/badge/Firefly-OpenCore%20Banking%20Platform-red.svg)](https://getfirefly.io)

**A unified, reactive Event-Driven Architecture library for the Firefly OpenCore Banking Platform**

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Quick Start](#quick-start)
  - [Add Dependency](#1-add-dependency)
  - [Configure Properties](#2-configure-properties)
  - [Publish Events](#3-publish-events)
  - [Consume Events](#4-consume-events)
- [Supported Messaging Platforms](#supported-messaging-platforms)
- [Architecture](#architecture)
- [Configuration](#configuration)
  - [Basic Configuration](#basic-configuration)
  - [Publisher Configuration](#publisher-configuration)
  - [Resilience Configuration](#resilience-configuration)
- [Publishing Features](#publishing-features)
  - [@PublishResult Annotation](#publishresult-annotation-features)
  - [Conditional Publishing](#conditional-publishing-with-spel)
  - [Dynamic Destinations](#dynamic-destinations-and-keys)
  - [Dynamic Topic Selection](#dynamic-topic-selection-with-eventpublisherfactory)
  - [Error Publishing](#error-publishing)
- [Event Listener Features](#event-listener-features)
  - [Basic Event Listening](#basic-event-listening)
  - [Advanced Filtering](#advanced-filtering-and-routing)
  - [Consumer Groups](#consumer-groups-and-acknowledgments)
  - [Error Handling](#error-handling-and-retry-strategies)
  - [Priority Processing](#synchronous-vs-asynchronous-processing)
  - [Multi-Platform Support](#multiple-platform-support)
- [Metrics & Monitoring](#metrics--monitoring)
- [Health Checks](#health-checks)
- [Testing Support](#testing-support)
- [Performance](#performance)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

The Firefly Event Driven Architecture (EDA) Library is a comprehensive, production-ready solution for implementing event-driven patterns in modern Spring Boot applications. Built as part of the Firefly OpenCore Banking Platform, it provides standardized abstractions and implementations for publishing and consuming events across multiple messaging platforms.

## Key Features

### 🚀 **Multi-Platform Support**
- **Apache Kafka** - High-throughput streaming with partitioning and persistence
- **RabbitMQ** - Advanced routing with flexible exchanges and queues
- **Spring Application Events** - In-memory event processing for internal communication
- **NOOP Publisher** - Disabled/testing mode that discards messages

### ⚡ **Reactive & Asynchronous**
- Built on **Project Reactor** for non-blocking, asynchronous processing
- **Reactive streams** integration with full Spring WebFlux compatibility
- **Async/sync processing** options with configurable threading models
- **Backpressure support** built into reactive streams

### 🛡️ **Production-Ready Resilience**
- **Circuit Breaker** patterns using Resilience4j with configurable failure thresholds
- **Retry mechanisms** with configurable backoff strategies
- **Rate limiting** to prevent system overload
- **Timeout management** with configurable processing windows
- **Error handling strategies** including dead letter queue support

### 🎯 **Advanced Event Processing**
- **@EventListener** annotation with powerful filtering capabilities
- **SpEL expressions** for complex conditional event processing
- **Priority-based routing** for critical event handling
- **Glob pattern matching** for flexible event type filtering
- **Consumer groups** for load balancing and parallel processing (Kafka)
- **Manual acknowledgments** for guaranteed message processing

### 🔧 **Developer Experience**
- **Spring Boot auto-configuration** with sensible defaults
- **@PublishResult** annotation for declarative event publishing
- **Conditional publishing** with SpEL-based business rules
- **Dynamic destinations** and routing keys with runtime evaluation
- **Dynamic topic selection** with EventPublisherFactory to override application properties
- **Header propagation** and custom metadata support
- **Comprehensive error handling** with multiple strategies

### 📊 **Observability & Monitoring**
- **Micrometer metrics** integration with detailed publishing and consumption statistics
- **Publisher health checks** with availability monitoring
- **Circuit breaker, retry, and rate limiter metrics** tracking
- **Performance monitoring** with latency and throughput metrics

### 🧪 **Testing & Development**
- **TestContainers** integration for integration testing
- **Multiple publisher implementations** for different environments
- **NOOP publisher** for testing scenarios
- **Comprehensive test suite** with platform-specific integration tests

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-eda</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Properties

> **⚠️ IMPORTANT - Hexagonal Architecture Principle:**
>
> **ALWAYS use `firefly.eda.*` properties exclusively.** NEVER configure Spring-specific properties like `spring.kafka.*` or `spring.rabbitmq.*` directly. The library follows hexagonal architecture principles and manages all provider-specific configurations internally.
>
> ✅ **Correct:** `firefly.eda.publishers.kafka.default.bootstrap-servers`
> ❌ **Incorrect:** `spring.kafka.bootstrap-servers`

```yaml
firefly:
  eda:
    enabled: true
    default-publisher-type: kafka
    publishers:
      kafka:
        default:
          bootstrap-servers: localhost:9092
          default-topic: events
```

### 3. Publish Events

#### Using @PublishResult Annotation (Recommended)

```java
@Service
public class OrderService {
    
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        destination = "order-events",
        eventType = "order.created",
        async = true
    )
    public Mono<Order> createOrder(CreateOrderRequest request) {
        return processOrder(request)
            .doOnSuccess(order -> log.info("Order created: {}", order.getId()));
    }
}
```

#### Using EventPublisher Directly

```java
@Service
public class OrderService {
    
    @Autowired
    private EventPublisherFactory publisherFactory;
    
    public Mono<Void> createOrder(Order order) {
        return processOrder(order)
            .then(publishOrderCreated(order));
    }
    
    private Mono<Void> publishOrderCreated(Order order) {
        EventPublisher publisher = publisherFactory.getDefaultPublisher();
        return publisher.publish(
            new OrderCreatedEvent(order.getId(), order.getCustomerId()),
            "order-events",
            Map.of(
                "transaction-id", UUID.randomUUID().toString(),
                "event-type", "order.created"
            )
        );
    }
}
```

### 4. Consume Events

```java
@Component
public class OrderEventHandler {
    
    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        consumerType = PublisherType.KAFKA
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return processOrderCreated(event)
            .then(envelope.acknowledge());
    }
}
```

## Supported Messaging Platforms

| Platform | Publisher | Consumer | Features |
|----------|-----------|----------|---------|
| **Apache Kafka** | ✅ | ✅ | High-throughput, partitioning, persistence, ordering |
| **RabbitMQ** | ✅ | ✅ | Advanced routing, flexible exchanges, guaranteed delivery |
| **Spring Events** | ✅ | ✅ | In-memory, synchronous processing, testing |
| **NOOP** | ✅ | ✅ | Testing/disabled mode |

## Architecture

The library follows a layered architecture with clear separation of concerns:

```
┌─────────────────┐    ┌─────────────────┐
│   Application   │    │   @EventListener │
│     Layer       │◄───┤   Annotations   │
└─────────────────┘    └─────────────────┘
         │                       ▲
         ▼                       │
┌─────────────────┐    ┌─────────────────┐
│ EDA Abstraction │    │ Event Consumer  │
│     Layer       │◄───┤   Processing    │
└─────────────────┘    └─────────────────┘
         │
         ▼
┌─────────────────┐    ┌─────────────────┐
│   Publisher     │    │   Resilience    │
│   Factory       │◄───┤    Patterns     │
└─────────────────┘    └─────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│        Messaging Platform Layer         │
├─────────────────┬─────────────────┬─────┤
│     Kafka       │    RabbitMQ     │Apps │
│   Publishers/   │   Publishers/   │Event│
│   Consumers     │   Consumers     │NOOP │
└─────────────────┴─────────────────┴─────┘
```

## Configuration

> **⚠️ CRITICAL - Configuration Namespace:**
>
> This library follows **hexagonal architecture** principles. ALL configuration MUST be under the `firefly.eda.*` namespace.
>
> **DO NOT use provider-specific Spring properties:**
> - ❌ `spring.kafka.*` - Will be IGNORED
> - ❌ `spring.rabbitmq.*` - Will be IGNORED
> - ✅ `firefly.eda.publishers.kafka.*` - CORRECT
> - ✅ `firefly.eda.publishers.rabbitmq.*` - CORRECT
> - ✅ `firefly.eda.consumer.kafka.*` - CORRECT
> - ✅ `firefly.eda.consumer.rabbitmq.*` - CORRECT
>
> The library internally manages all provider-specific configurations, ensuring complete abstraction from messaging platform implementations.

### Basic Configuration

```yaml
firefly:
  eda:
    enabled: true
    default-publisher-type: AUTO  # Auto-select best available
    default-connection-id: default
    default-timeout: 30s
    metrics-enabled: true
    health-enabled: true
```

### Publisher Configuration

> **💡 Advanced Provider-Specific Properties:**
>
> Use the `properties` map to pass advanced provider-specific settings (e.g., Kafka's `acks`, `retries`, `compression.type`). The library will forward these to the underlying provider while maintaining abstraction.

```yaml
firefly:
  eda:
    publishers:
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          default-topic: events
          # Advanced Kafka-specific properties via the properties map
          properties:
            acks: all
            retries: 3
            compression.type: gzip
            max.in.flight.requests.per.connection: 5

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

      application-event:
        enabled: true
        default-destination: application-events
```

### Consumer Configuration

> **💡 Consumer-Specific Advanced Properties:**
>
> Like publishers, consumers also support the `properties` map for advanced provider-specific settings (e.g., Kafka's `fetch.max.wait.ms`, `metadata.max.age.ms`).

```yaml
firefly:
  eda:
    consumer:
      enabled: true  # Global consumer toggle
      group-id: my-service-group
      concurrency: 3

      # Kafka consumer
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          topics: events
          auto-offset-reset: earliest
          # Advanced Kafka consumer properties via the properties map
          properties:
            fetch.max.wait.ms: 500
            metadata.max.age.ms: 300000
            max.poll.records: 500

      # RabbitMQ consumer
      rabbitmq:
        default:
          enabled: true
          host: localhost
          port: 5672
          username: guest
          password: guest
          virtual-host: /
          queues: events-queue
          concurrent-consumers: 2
          max-concurrent-consumers: 10
          prefetch-count: 20

      # Application Event consumer (in-memory)
      application-event:
        enabled: true

      # NOOP consumer (for testing)
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
        wait-duration-in-open-state: 60s
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 500ms
      rate-limiter:
        enabled: true
        limit-for-period: 100
        limit-refresh-period: 1s
```


## Publishing Features

### @PublishResult Annotation Features

#### Conditional Publishing with SpEL
```java
@PublishResult(
    destination = "user-events",
    eventType = "user.created",
    condition = "#result.isActive() and #result.getRole() == 'PREMIUM'",
    headers = {"priority=high", "source=user-service"}
)
public Mono<User> createPremiumUser(CreateUserRequest request) {
    return userRepository.save(new User(request));
}
```

#### Dynamic Destinations and Keys
```java
@PublishResult(
    destination = "#{#result.getAccountType()}-events",
    key = "#{#result.getCustomerId()}",
    eventType = "account.created"
)
public Mono<Account> createAccount(CreateAccountRequest request) {
    return accountService.createAccount(request);
}
```

#### Dynamic Topic Selection with EventPublisherFactory

The EventPublisherFactory supports dynamic topic/destination selection at runtime, allowing you to override the default destinations configured in application properties without modifying configuration files.

**Key Features:**
- Override default destinations at runtime
- Support for all publisher types (Kafka, RabbitMQ, Spring Application Events)
- Destination resolution priority: explicit destination > custom default > configured default
- Maintains all publisher features (health checks, resilience, metrics)

**Basic Usage:**
```java
@Service
public class UserService {

    private final EventPublisherFactory publisherFactory;

    public void publishUserEvent(UserRegisteredEvent event, String tenantId) {
        // Get publisher with custom default destination
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA,
            "tenant-" + tenantId + "-user-events"
        );

        // Publish without specifying destination - uses custom default
        publisher.publish(event, null).subscribe();

        // Publish with explicit destination - overrides custom default
        publisher.publish(event, "special-events").subscribe();
    }
}
```

**Multi-Service Architecture Example:**
```java
@Service
public class EventRoutingService {

    private final EventPublisherFactory publisherFactory;

    public void routeEventsByService(Object event, String serviceType) {
        // Create service-specific publishers
        EventPublisher userPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA, "user-service-events");
        EventPublisher orderPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA, "order-service-events");
        EventPublisher auditPublisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA, "audit-events");

        // Route to appropriate service topic
        switch (serviceType) {
            case "user" -> userPublisher.publish(event, null);
            case "order" -> orderPublisher.publish(event, null);
            default -> auditPublisher.publish(event, null);
        }
    }
}
```

**Connection-Specific Destinations:**
```java
// Get publisher with specific connection and custom destination
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA,
    "secondary-cluster",  // connection ID
    "high-priority-events" // custom destination
);
```

**Available Methods:**
- `getPublisherWithDestination(PublisherType, String customDestination)`
- `getPublisherWithDestination(PublisherType, String connectionId, String customDestination)`
- `getDefaultPublisherWithDestination(String customDestination)`

#### Error Publishing
```java
@PublishResult(
    destination = "error-events",
    eventType = "order.processing.failed",
    publishOnError = true,
    async = false
)
public Mono<Order> processOrder(ProcessOrderRequest request) {
    return orderProcessor.process(request);
}
```

## Event Listener Features

### Basic Event Listening

```java
@Component
public class OrderEventHandler {
    
    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        consumerType = PublisherType.KAFKA
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return processOrderCreated(event)
            .then(envelope.acknowledge());
    }
}
```

### Advanced Filtering and Routing

```java
@EventListener(
    destinations = {"user-events", "order-events"},
    eventTypes = {"*.created", "*.updated"},  // Glob patterns
    condition = "#envelope.headers['priority'] == 'high'", // SpEL filtering
    priority = 100,  // Higher priority = processed first
    consumerType = PublisherType.KAFKA,
    connectionId = "primary"
)
public Mono<Void> handleHighPriorityEvents(EventEnvelope envelope) {
    return processHighPriorityEvent(envelope.payload())
        .then(envelope.acknowledge());
}
```

### Consumer Groups and Acknowledgments

```java
@EventListener(
    destinations = "payment-events",
    eventTypes = "payment.*",
    groupId = "payment-processing-group",
    autoAck = false,  // Manual acknowledgment
    async = true,
    timeoutMs = 30000
)
public Mono<Void> handlePaymentEvents(EventEnvelope envelope) {
    return processPayment(envelope.payload())
        .then(envelope.acknowledge())
        .onErrorResume(error -> {
            log.error("Payment processing failed", error);
            return envelope.reject(error);
        });
}
```

### Error Handling and Retry Strategies

```java
@EventListener(
    destinations = "critical-operations",
    errorStrategy = ErrorHandlingStrategy.DEAD_LETTER,
    maxRetries = 5,
    retryDelayMs = 2000,
    timeoutMs = 30000
)
public Mono<Void> handleCriticalOperations(EventEnvelope envelope) {
    return criticalService.process(envelope.payload())
        .timeout(Duration.ofSeconds(30))
        .doOnSuccess(result -> log.info("Critical operation completed: {}", result))
        .then(envelope.acknowledge());
}
```

### Synchronous vs Asynchronous Processing

```java
// Asynchronous processing (default)
@EventListener(
    destinations = "audit-events",
    async = true,
    priority = 1
)
public Mono<Void> handleAuditEventsAsync(EventEnvelope envelope) {
    return auditService.logEventAsync(envelope.payload())
        .then(envelope.acknowledge());
}

// Synchronous processing
@EventListener(
    destinations = "sync-operations",
    async = false,
    timeoutMs = 5000
)
public Mono<Void> handleSyncOperations(EventEnvelope envelope) {
    return syncService.processImmediately(envelope.payload())
        .then(envelope.acknowledge());
}
```

### Multiple Platform Support

```java
@Component
public class MultiPlatformEventHandler {
    
    // Kafka consumer
    @EventListener(
        destinations = "kafka-events",
        consumerType = PublisherType.KAFKA,
        connectionId = "primary-kafka",
        groupId = "kafka-consumer-group"
    )
    public Mono<Void> handleKafkaEvents(EventEnvelope envelope) {
        return processKafkaEvent(envelope);
    }
    
    // RabbitMQ consumer
    @EventListener(
        destinations = "rabbitmq-events",
        consumerType = PublisherType.RABBITMQ,
        connectionId = "primary-rabbitmq"
    )
    public Mono<Void> handleRabbitMqEvents(EventEnvelope envelope) {
        return processRabbitMqEvent(envelope);
    }
    
    // Spring Events consumer
    @EventListener(
        eventTypes = "internal.*",
        consumerType = PublisherType.APPLICATION_EVENT
    )
    public Mono<Void> handleSpringEvents(EventEnvelope envelope) {
        return processInternalEvent(envelope);
    }
}
```

## Metrics & Monitoring

The library provides comprehensive metrics via Micrometer:

- **Publishing Metrics**: Success/failure counts, latency, message sizes
- **Consumption Metrics**: Processing times, throughput, error rates
- **Health Metrics**: Publisher/consumer availability and status
- **Resilience Metrics**: Circuit breaker states, retry attempts, rate limiter rejections

### Available Metrics

```
firefly.eda.publish.count{publisher_type, destination, status}
firefly.eda.publish.duration{publisher_type, destination, event_type, status}
firefly.eda.publish.message.size{publisher_type, destination}
firefly.eda.consume.count{consumer_type, source, status}
firefly.eda.consume.duration{consumer_type, source, event_type, status}
firefly.eda.publisher.health{publisher_type, connection_id}
firefly.eda.consumer.health{consumer_type}
```

## Health Checks

Built-in health indicator for Spring Boot Actuator (automatically enabled):

```bash
curl http://localhost:8080/actuator/health
```

Example health response:

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
    "consumers": {},
    "resilience": {
      "enabled": true,
      "circuitBreakerEnabled": true,
      "retryEnabled": true
    }
  }
}
```

## Testing Support

Built-in testing utilities with TestContainers integration:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.eda.publishers.kafka.default.bootstrap-servers=${embedded.kafka.brokers}"
})
class OrderServiceTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka"))
            .withExposedPorts(9093);
    
    @Test
    void shouldPublishOrderCreatedEvent() {
        // Test event publishing
    }
}
```

## Documentation

- 📖 [Architecture Guide](docs/ARCHITECTURE.md)
- 🚀 [Quick Start Guide](docs/QUICKSTART.md)
- 👨‍💻 [Developer Guide](docs/DEVELOPER_GUIDE.md)
- 📚 [API Reference](docs/API_REFERENCE.md)
- 💡 [Examples](docs/EXAMPLES.md)
- 🎯 [EventListener Advanced Examples](docs/EVENTLISTENER_EXAMPLES.md)
- 🔧 [Configuration Reference](docs/CONFIGURATION.md)

## Contributing

We welcome contributions! Please read our [Contributing Guide](docs/CONTRIBUTING.md) for details on:

- Code of conduct
- Development setup
- Pull request process
- Coding standards

## License

Copyright © 2025 Firefly Software Solutions Inc

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- 🐛 **Issues**: [GitHub Issues](https://github.com/firefly-oss/lib-common-eda/issues)
- 📖 **Documentation**: [Wiki](https://github.com/firefly-oss/lib-common-eda/wiki)
- 🌐 **Website**: [getfirefly.io](https://getfirefly.io)

---

**Part of the [Firefly OpenCore Banking Platform](https://getfirefly.io)** 🏦
