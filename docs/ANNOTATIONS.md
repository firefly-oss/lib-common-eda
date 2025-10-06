# Event-Driven Architecture Annotations

This document provides comprehensive documentation for the EDA library's annotation-based programming model.

## Table of Contents

- [Overview](#overview)
- [@EventPublisher](#eventpublisher)
- [@PublishResult](#publishresult)
- [@EventListener](#eventlistener)
- [Error Handling Strategies](#error-handling-strategies)
- [Publisher Types](#publisher-types)
- [Best Practices](#best-practices)
- [Examples](#examples)

## Overview

The Firefly EDA library provides three main annotations for declarative event-driven programming:

1. **`@EventPublisher`** - Publish events from method parameters (before/after execution)
2. **`@PublishResult`** - Automatically publish method return values as events
3. **`@EventListener`** - Subscribe to and handle events from messaging systems

These annotations support:
- Multiple messaging platforms (Kafka, RabbitMQ, Spring Events, NOOP)
- SpEL expressions for dynamic configuration
- Conditional publishing/consumption
- Custom serialization
- Error handling strategies
- Retry logic
- Circuit breaker patterns

## @EventPublisher

The `@EventPublisher` annotation marks methods for publishing events from method parameters.

### Basic Usage

```java
@EventPublisher(
    publisherType = PublisherType.KAFKA,
    destination = "user-commands",
    eventType = "user.create.command"
)
public Mono<User> createUser(CreateUserCommand command) {
    // Command is published before method execution
    return userService.create(command);
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `publisherType` | `PublisherType` | `AUTO` | The messaging platform to use |
| `destination` | `String` | `""` | Topic/queue/channel name (supports SpEL) |
| `eventType` | `String` | `""` | Event type for routing/filtering (supports SpEL) |
| `connectionId` | `String` | `"default"` | Connection configuration ID |
| `serializer` | `String` | `""` | Custom serializer bean name |
| `async` | `boolean` | `true` | Whether to publish asynchronously |
| `timeoutMs` | `long` | `0` | Publication timeout (0 = default) |
| `parameterIndex` | `int` | `-1` | Which parameter to publish (-1 = all) |
| `condition` | `String` | `""` | SpEL condition for conditional publishing |
| `key` | `String` | `""` | Message key for partitioning (supports SpEL) |
| `headers` | `String[]` | `{}` | Custom headers (supports SpEL) |
| `timing` | `PublishTiming` | `BEFORE` | When to publish (BEFORE/AFTER/BOTH) |

### Publish Timing

Control when events are published relative to method execution:

```java
// Publish before method execution (default)
@EventPublisher(timing = PublishTiming.BEFORE)
public Mono<User> createUser(CreateUserCommand command) { ... }

// Publish after method execution
@EventPublisher(timing = PublishTiming.AFTER)
public Mono<User> createUser(CreateUserCommand command) { ... }

// Publish both before and after
@EventPublisher(timing = PublishTiming.BOTH)
public Mono<User> createUser(CreateUserCommand command) { ... }
```

### Parameter Selection

```java
// Publish all parameters (wrapped in a map)
@EventPublisher(parameterIndex = -1)
public void auditAction(String userId, String action, Map<String, Object> details) { ... }

// Publish only the first parameter
@EventPublisher(parameterIndex = 0)
public Mono<User> createUser(CreateUserCommand command, AuditContext context) { ... }
```

### SpEL Expressions

```java
@EventPublisher(
    destination = "#{#param0.type}-events",
    condition = "#param0 != null && #param0.isValid()",
    key = "#param0.userId",
    headers = {"source=api", "userId=#{#param0.userId}"}
)
public Mono<Result> processEvent(EventData data) { ... }
```

### Combining with @PublishResult

```java
@EventPublisher(
    destination = "user-commands",
    eventType = "user.create.requested"
)
@PublishResult(
    destination = "user-events",
    eventType = "user.created"
)
public Mono<User> createUser(CreateUserRequest request) {
    // Publishes request before execution
    // Publishes result after execution
}
```

## @PublishResult

The `@PublishResult` annotation automatically publishes method return values as events.

### Basic Usage

```java
@PublishResult(
    publisherType = PublisherType.KAFKA,
    destination = "user-events",
    eventType = "user.created"
)
public Mono<User> createUser(CreateUserRequest request) {
    // Return value is automatically published
    return userService.create(request);
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `publisherType` | `PublisherType` | `AUTO` | The messaging platform to use |
| `destination` | `String` | `""` | Topic/queue/channel name (supports SpEL) |
| `eventType` | `String` | `""` | Event type for routing/filtering (supports SpEL) |
| `connectionId` | `String` | `"default"` | Connection configuration ID |
| `serializer` | `String` | `""` | Custom serializer bean name |
| `async` | `boolean` | `true` | Whether to publish asynchronously |
| `timeoutMs` | `long` | `0` | Publication timeout (0 = default) |
| `publishOnError` | `boolean` | `false` | Publish even if method throws exception |
| `condition` | `String` | `""` | SpEL condition for conditional publishing |
| `key` | `String` | `""` | Message key for partitioning (supports SpEL) |
| `headers` | `String[]` | `{}` | Custom headers (supports SpEL) |

### Conditional Publishing

```java
@PublishResult(
    destination = "user-events",
    eventType = "user.created",
    condition = "#result != null && #result.isActive()"
)
public Mono<User> createUser(CreateUserRequest request) { ... }
```

### Dynamic Destinations

```java
@PublishResult(
    destination = "#{#result.type}-events",
    eventType = "#{#result.status}.updated"
)
public Mono<Order> updateOrder(UpdateOrderRequest request) { ... }
```

### Publishing on Error

```java
@PublishResult(
    destination = "error-events",
    eventType = "user.creation.failed",
    publishOnError = true
)
public Mono<User> createUser(CreateUserRequest request) { ... }
```

### Custom Headers

```java
@PublishResult(
    destination = "user-events",
    headers = {
        "source=api",
        "version=1.0",
        "userId=#{#result.id}",
        "timestamp=#{T(java.time.Instant).now()}"
    }
)
public Mono<User> createUser(CreateUserRequest request) { ... }
```

## @EventListener

The `@EventListener` annotation marks methods as event consumers.

### Basic Usage

```java
@EventListener(
    destinations = {"user-events"},
    eventTypes = {"user.created", "user.updated"},
    consumerType = PublisherType.KAFKA
)
public Mono<Void> handleUserEvents(EventEnvelope event) {
    // Process the event
    return processEvent(event);
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `destinations` | `String[]` | `{}` | Topics/queues to listen on |
| `eventTypes` | `String[]` | `{}` | Event types to handle (supports wildcards) |
| `consumerType` | `PublisherType` | `AUTO` | The messaging platform to use |
| `connectionId` | `String` | `"default"` | Connection configuration ID |
| `errorStrategy` | `ErrorHandlingStrategy` | `LOG_AND_CONTINUE` | Error handling strategy |
| `maxRetries` | `int` | `3` | Maximum retry attempts |
| `retryDelayMs` | `long` | `1000` | Delay between retries |
| `autoAck` | `boolean` | `true` | Auto-acknowledge messages |
| `groupId` | `String` | `""` | Consumer group ID (Kafka) |
| `condition` | `String` | `""` | SpEL filter condition |
| `priority` | `int` | `0` | Listener priority (higher = first) |
| `async` | `boolean` | `true` | Async processing |
| `timeoutMs` | `long` | `0` | Processing timeout |

### Wildcard Event Types

```java
@EventListener(
    destinations = {"events"},
    eventTypes = {"user.*", "order.*"}
)
public Mono<Void> handleEvents(EventEnvelope event) { ... }
```

### Multiple Destinations

```java
@EventListener(
    destinations = {"topic1", "topic2", "topic3"},
    eventTypes = {"important.*"}
)
public Mono<Void> handleImportantEvents(EventEnvelope event) { ... }
```

### Conditional Processing

```java
@EventListener(
    destinations = {"user-events"},
    condition = "#event.headers['priority'] == 'high'"
)
public Mono<Void> handleHighPriorityEvents(EventEnvelope event) { ... }
```

### Manual Acknowledgment

```java
@EventListener(
    destinations = {"orders"},
    autoAck = false
)
public Mono<Void> handleOrder(EventEnvelope event) {
    return processOrder(event)
        .doOnSuccess(v -> event.getAckCallback().acknowledge())
        .doOnError(e -> event.getAckCallback().reject());
}
```

### Priority-Based Processing

```java
@EventListener(destinations = {"events"}, priority = 100)
public Mono<Void> highPriorityHandler(EventEnvelope event) { ... }

@EventListener(destinations = {"events"}, priority = 50)
public Mono<Void> mediumPriorityHandler(EventEnvelope event) { ... }

@EventListener(destinations = {"events"}, priority = 10)
public Mono<Void> lowPriorityHandler(EventEnvelope event) { ... }
```

## Error Handling Strategies

The library provides several error handling strategies:

### LOG_AND_CONTINUE

Log the error and continue processing other events. The failed event is acknowledged.

```java
@EventListener(errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE)
public Mono<Void> handleEvent(EventEnvelope event) { ... }
```

### LOG_AND_RETRY

Log the error and retry processing up to `maxRetries` times.

```java
@EventListener(
    errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
    maxRetries = 5,
    retryDelayMs = 2000
)
public Mono<Void> handleEvent(EventEnvelope event) { ... }
```

### REJECT_AND_STOP

Reject the event and stop processing further events.

```java
@EventListener(errorStrategy = ErrorHandlingStrategy.REJECT_AND_STOP)
public Mono<Void> handleCriticalEvent(EventEnvelope event) { ... }
```

### DEAD_LETTER

Send failed events to a dead letter queue/topic.

```java
@EventListener(errorStrategy = ErrorHandlingStrategy.DEAD_LETTER)
public Mono<Void> handleEvent(EventEnvelope event) { ... }
```

### IGNORE

Silently ignore errors and acknowledge the message.

```java
@EventListener(errorStrategy = ErrorHandlingStrategy.IGNORE)
public Mono<Void> handleNonCriticalEvent(EventEnvelope event) { ... }
```

### CUSTOM

Delegate error handling to a custom handler.

```java
@EventListener(errorStrategy = ErrorHandlingStrategy.CUSTOM)
public Mono<Void> handleEvent(EventEnvelope event) { ... }
```

## Publisher Types

The library supports multiple messaging platforms:

- `AUTO` - Automatically detect available publisher
- `KAFKA` - Apache Kafka
- `RABBITMQ` - RabbitMQ
- `APPLICATION_EVENT` - Spring Application Events
- `NOOP` - No-operation publisher for testing

## Best Practices

1. **Use Specific Event Types**: Define clear, hierarchical event types (e.g., `user.created`, `order.shipped`)

2. **Leverage SpEL for Dynamic Configuration**: Use SpEL expressions for dynamic destinations and conditions

3. **Choose Appropriate Error Strategies**: Match error handling to business requirements

4. **Use Manual Acknowledgment for Critical Events**: Disable `autoAck` for events requiring guaranteed processing

5. **Set Reasonable Timeouts**: Configure timeouts to prevent hanging operations

6. **Use Priority for Ordering**: Set listener priorities when order matters

7. **Combine Annotations Wisely**: Use `@EventPublisher` and `@PublishResult` together for command/event patterns

8. **Test Thoroughly**: Write integration tests for all event flows

## Examples

See the [examples directory](../examples) for complete working examples of:
- CQRS with event sourcing
- Saga patterns
- Event-driven microservices
- Multi-platform event routing
- Error handling and retry strategies

