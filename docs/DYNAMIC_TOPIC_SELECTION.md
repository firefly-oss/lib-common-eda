# Dynamic Topic Selection API Reference

## Overview

The Dynamic Topic Selection feature allows you to override default destinations configured in application properties at runtime using the EventPublisherFactory. This is particularly useful for multi-tenant applications, microservice architectures, or scenarios where routing logic needs to be determined dynamically.

## API Methods

### EventPublisherFactory Methods

#### `getPublisherWithDestination(PublisherType publisherType, String customDefaultDestination)`

Creates an EventPublisher with a custom default destination using the default connection.

**Parameters:**
- `publisherType` - The type of publisher (KAFKA, RABBITMQ, APPLICATION_EVENT, NOOP)
- `customDefaultDestination` - The custom default destination to use

**Returns:** `EventPublisher` or `null` if the base publisher is not available

**Example:**
```java
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, 
    "user-events"
);
```

#### `getPublisherWithDestination(PublisherType publisherType, String connectionId, String customDefaultDestination)`

Creates an EventPublisher with a custom default destination using a specific connection.

**Parameters:**
- `publisherType` - The type of publisher
- `connectionId` - The connection ID to use
- `customDefaultDestination` - The custom default destination to use

**Returns:** `EventPublisher` or `null` if the base publisher is not available

**Example:**
```java
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, 
    "secondary-cluster",
    "high-priority-events"
);
```

#### `getDefaultPublisherWithDestination(String customDefaultDestination)`

Creates the default EventPublisher with a custom default destination.

**Parameters:**
- `customDefaultDestination` - The custom default destination to use

**Returns:** `EventPublisher` or `null` if the default publisher is not available

**Example:**
```java
EventPublisher publisher = publisherFactory.getDefaultPublisherWithDestination("audit-events");
```

## Destination Resolution Priority

When publishing events, destinations are resolved in the following priority order:

1. **Explicit Destination** - Destination provided in the `publish()` method call
2. **Custom Default Destination** - Destination specified when creating the publisher
3. **Configured Default Destination** - Destination from application properties

### Example:

```java
// Publisher created with custom default destination
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, "user-events");

// Uses custom default: "user-events"
publisher.publish(event, null);

// Uses explicit destination: "special-events" (overrides custom default)
publisher.publish(event, "special-events");
```

## Use Cases

### Multi-Tenant Applications

```java
@Service
public class TenantAwareEventService {
    
    private final EventPublisherFactory publisherFactory;
    
    public void publishTenantEvent(Object event, String tenantId) {
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA, 
            "tenant-" + tenantId + "-events"
        );
        publisher.publish(event, null).subscribe();
    }
}
```

### Environment-Specific Routing

```java
@Service
public class EnvironmentAwarePublisher {
    
    @Value("${app.environment}")
    private String environment;
    
    private final EventPublisherFactory publisherFactory;
    
    public void publishWithEnvironmentPrefix(Object event) {
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
            PublisherType.KAFKA, 
            environment + "-events"
        );
        publisher.publish(event, null).subscribe();
    }
}
```

### Service-Specific Event Routing

```java
@Service
public class ServiceEventRouter {
    
    private final EventPublisherFactory publisherFactory;
    private final Map<String, EventPublisher> servicePublishers;
    
    @PostConstruct
    public void initializePublishers() {
        servicePublishers = Map.of(
            "user", publisherFactory.getPublisherWithDestination(PublisherType.KAFKA, "user-service-events"),
            "order", publisherFactory.getPublisherWithDestination(PublisherType.KAFKA, "order-service-events"),
            "payment", publisherFactory.getPublisherWithDestination(PublisherType.KAFKA, "payment-service-events")
        );
    }
    
    public void routeEvent(Object event, String serviceType) {
        EventPublisher publisher = servicePublishers.getOrDefault(serviceType, 
            publisherFactory.getDefaultPublisherWithDestination("unknown-service-events"));
        publisher.publish(event, null).subscribe();
    }
}
```

## Integration with Existing Features

### Resilience

Dynamic topic selection works seamlessly with the resilience features:

```java
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, "resilient-events");

// Circuit breaker, retry, and other resilience patterns are automatically applied
publisher.publish(event, null).subscribe();
```

### Health Checks

Publishers created with custom destinations maintain full health check capabilities:

```java
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, "health-monitored-events");

// Health information includes custom destination details
Mono<PublisherHealth> healthMono = publisher.getHealth();
healthMono.subscribe(health -> {
    log.info("Publisher health: {}", health.getStatus());
    log.info("Custom destination: {}", health.getDetails().get("customDefaultDestination"));
});
```

### Metrics

All metrics and monitoring continue to work with dynamically configured publishers:

```java
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, "metrics-tracked-events");

// Publishing metrics are tracked normally
publisher.publish(event, null).subscribe();
```

## Best Practices

1. **Cache Publishers**: Create publishers once and reuse them rather than creating new ones for each publish operation
2. **Null Checks**: Always check if the returned publisher is null before using it
3. **Meaningful Names**: Use descriptive destination names that clearly indicate the purpose
4. **Environment Separation**: Use environment prefixes to separate events across different environments
5. **Tenant Isolation**: In multi-tenant applications, ensure proper tenant isolation in destination names

## Error Handling

```java
EventPublisher publisher = publisherFactory.getPublisherWithDestination(
    PublisherType.KAFKA, "error-prone-events");

if (publisher == null) {
    log.warn("Publisher not available for KAFKA");
    return;
}

publisher.publish(event, null)
    .doOnError(error -> log.error("Failed to publish event", error))
    .subscribe();
```
