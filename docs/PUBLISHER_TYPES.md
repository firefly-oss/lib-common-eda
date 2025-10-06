# Publisher Types Reference

This document provides a comprehensive reference for all supported publisher types in the Firefly EDA library.

## Supported Publisher Types

The `PublisherType` enum defines the following supported messaging platforms:

### AUTO
**Description**: Automatically select the best available publisher  
**Selection Priority**: KAFKA → RABBITMQ → APPLICATION_EVENT → NOOP  
**Use Case**: Let the system choose the optimal publisher based on availability and configuration

```java
@PublishResult(publisherType = PublisherType.AUTO)
public Mono<User> createUser(CreateUserRequest request) {
    return userService.create(request);
}
```

### KAFKA
**Description**: Apache Kafka  
**Features**: High-throughput, partitioning, persistence, ordering  
**Best For**: Event sourcing, real-time data streaming, high-volume events  
**Persistence**: ✅ Yes  
**Ordering**: ✅ Yes (per partition)  
**Cloud Service**: ❌ No (self-hosted)

```yaml
firefly:
  eda:
    publishers:
      kafka:
        default:
          bootstrap-servers: localhost:9092
          default-topic: events
          properties:
            acks: all
            retries: 3
```

### RABBITMQ
**Description**: RabbitMQ AMQP Broker  
**Features**: Advanced routing, flexible exchanges, guaranteed delivery  
**Best For**: Complex messaging patterns, reliable delivery, flexible routing  
**Persistence**: ✅ Yes  
**Ordering**: ❌ No (depends on routing)  
**Cloud Service**: ❌ No (self-hosted)

```yaml
firefly:
  eda:
    publishers:
      rabbitmq:
        default:
          host: localhost
          port: 5672
          username: guest
          password: guest
          default-exchange: events
```

### APPLICATION_EVENT
**Description**: Spring Application Event Bus (in-memory)  
**Features**: Synchronous processing, JVM-local, simple integration  
**Best For**: Single-instance applications, testing, internal communication  
**Persistence**: ❌ No  
**Ordering**: ❌ No  
**Cloud Service**: ❌ No

```yaml
firefly:
  eda:
    publishers:
      application-event:
        enabled: true
        default-destination: local-events
```

### NOOP
**Description**: No-operation publisher that discards all messages  
**Features**: Testing, disabled mode, development  
**Best For**: Testing scenarios, development without messaging  
**Persistence**: ❌ No  
**Ordering**: ❌ No  
**Cloud Service**: ❌ No

```java
@TestPropertySource(properties = {
    "firefly.eda.default-publisher-type=NOOP"
})
class ServiceTest {
    // All events will be discarded during testing
}
```

## Publisher Selection Strategy

When using `PublisherType.AUTO`, the system selects publishers in this priority order:

1. **KAFKA** - If Kafka is configured and available
2. **RABBITMQ** - If RabbitMQ is configured and available  
3. **APPLICATION_EVENT** - If Spring context is available (always true)
4. **NOOP** - If explicitly enabled for testing

## Feature Comparison Matrix

| Feature | KAFKA | RABBITMQ | APPLICATION_EVENT | NOOP |
|---------|-------|----------|------------------|------|
| **Throughput** | Very High | High | High | N/A |
| **Persistence** | ✅ | ✅ | ❌ | ❌ |
| **Ordering** | ✅ | ❌ | ❌ | ❌ |
| **Partitioning** | ✅ | ❌ | ❌ | ❌ |
| **Complex Routing** | ❌ | ✅ | ❌ | ❌ |
| **Guaranteed Delivery** | ✅ | ✅ | ❌ | ❌ |
| **Multi-Instance** | ✅ | ✅ | ❌ | ❌ |
| **Cloud Native** | ✅ | ✅ | ❌ | ❌ |
| **Setup Complexity** | Medium | Medium | Low | None |

## Configuration Examples

### Multiple Publishers
```yaml
firefly:
  eda:
    default-publisher-type: AUTO
    publishers:
      kafka:
        primary:
          bootstrap-servers: kafka-cluster:9092
          default-topic: events
        analytics:
          bootstrap-servers: analytics-kafka:9092
          default-topic: analytics
      
      rabbitmq:
        notifications:
          host: rabbitmq-host
          port: 5672
          default-exchange: notifications
      
      application-event:
        enabled: true
        default-destination: internal-events
```

### Publisher-Specific Configuration
```java
@Service
public class EventService {
    
    @Autowired
    private EventPublisherFactory publisherFactory;
    
    public Mono<Void> publishToKafka(Object event) {
        EventPublisher kafka = publisherFactory.getPublisher(PublisherType.KAFKA, "primary");
        return kafka.publish(event, "high-volume-events");
    }
    
    public Mono<Void> publishToRabbitMQ(Object event) {
        EventPublisher rabbit = publisherFactory.getPublisher(PublisherType.RABBITMQ, "notifications");
        return rabbit.publish(event, "user.notifications");
    }
}
```

## Best Practices

### When to Use Each Publisher

**Use KAFKA when:**
- High throughput is required (>10k events/second)
- Event ordering is important
- Long-term event storage is needed
- Building event-sourced systems
- Streaming analytics is required

**Use RABBITMQ when:**
- Complex routing patterns are needed
- Guaranteed delivery is critical
- Different message types need different handling
- Fan-out patterns are common
- Priority queues are required

**Use APPLICATION_EVENT when:**
- Simple internal communication is needed
- Single-instance deployment
- Low-latency local processing
- Testing scenarios
- Prototyping

**Use NOOP when:**
- Running tests without messaging
- Temporarily disabling events
- Development environments
- Performance testing without I/O

### Publisher Health Monitoring

All publishers support health checking:

```java
@Autowired
private EventPublisherFactory publisherFactory;

public Mono<Map<String, PublisherHealth>> checkHealth() {
    return Mono.fromCallable(() -> publisherFactory.getPublishersHealth())
        .map(healthMap -> {
            healthMap.forEach((type, health) -> {
                log.info("Publisher {}: {} ({})", 
                    type, health.isAvailable() ? "UP" : "DOWN", health.getStatus());
            });
            return healthMap;
        });
}
```

This reference provides complete information about all supported publisher types in the current implementation of the Firefly EDA library.