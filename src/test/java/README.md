# EDA Library Test Suite

This directory contains a comprehensive test suite for the Firefly Event-Driven Architecture (EDA) library.

## Overview

The test suite is designed following professional testing best practices and covers:

1. **Unit Tests** - Testing individual components in isolation
2. **Integration Tests** - End-to-end testing with real messaging infrastructure using Testcontainers
3. **Configuration Tests** - Verifying Spring Boot auto-configuration and properties

## Test Structure

```
src/test/java/
├── com/firefly/common/eda/
│   ├── config/                    # Auto-configuration tests
│   │   └── EdaAutoConfigurationTest.java
│   ├── integration/               # Integration tests with Testcontainers
│   │   ├── KafkaIntegrationTest.java
│   │   └── RabbitMqIntegrationTest.java
│   ├── properties/                # Configuration properties tests
│   │   └── EdaPropertiesTest.java
│   ├── publisher/                 # Publisher unit tests
│   │   └── SpringApplicationEventPublisherTest.java
│   ├── serialization/             # Serialization tests
│   │   └── JsonMessageSerializerTest.java
│   └── testconfig/                # Test infrastructure
│       ├── BaseIntegrationTest.java
│       ├── TestApplication.java
│       ├── TestContainersConfiguration.java
│       └── TestEventModels.java
└── README.md (this file)
```

## Test Categories

### 1. Configuration & Properties Tests

**Location:** `com.firefly.common.eda.properties`

Tests verify:
- Default property values are correctly applied
- Custom properties override defaults
- Validation constraints work properly
- Nested configuration objects are initialized
- Publisher configurations for all supported platforms

**Key Test:** `EdaPropertiesTest`

### 2. Auto-Configuration Tests

**Location:** `com.firefly.common.eda.config`

Tests verify:
- Spring Boot auto-configuration activates correctly
- Conditional beans are created based on properties
- Component scanning discovers EDA components
- Auto-configuration can be disabled via properties

**Key Test:** `EdaAutoConfigurationTest`

### 3. Serialization Tests

**Location:** `com.firefly.common.eda.serialization`

Tests verify:
- JSON serialization/deserialization
- Handling of complex nested objects
- Null value handling
- String and byte array handling
- Java Time types support
- Error handling for invalid data

**Key Test:** `JsonMessageSerializerTest`

### 4. Publisher Tests

**Location:** `com.firefly.common.eda.publisher`

Tests verify:
- Event publishing works correctly
- Headers and metadata are preserved
- Publisher availability checks
- Health status reporting
- Multiple event types handling

**Key Test:** `SpringApplicationEventPublisherTest`

### 5. Integration Tests

**Location:** `com.firefly.common.eda.integration`

Integration tests use **Testcontainers** to spin up real messaging infrastructure:

#### Kafka Integration Tests
- **Container:** Confluent Kafka 7.5.0
- **Tests:** Message publishing, consumption, header propagation, multiple messages
- **File:** `KafkaIntegrationTest.java`

#### RabbitMQ Integration Tests
- **Container:** RabbitMQ 3.12 with management plugin
- **Tests:** Exchange/routing key handling, message publishing, header propagation
- **File:** `RabbitMqIntegrationTest.java`

## Test Infrastructure

### BaseIntegrationTest

Base class for all integration tests providing:
- Spring Boot test context configuration
- Test profile activation
- Common setup/teardown hooks
- Utility methods for async testing

### TestApplication

Minimal Spring Boot application used as test context for all tests.

### TestContainersConfiguration

Provides reusable container instances for:
- Apache Kafka
- RabbitMQ

Containers are configured with `withReuse(true)` for performance.

### TestEventModels

Provides test event classes:
- `SimpleTestEvent` - Basic event with id, message, timestamp
- `OrderCreatedEvent` - Business event example
- `UserRegisteredEvent` - Another business event example
- `ComplexEvent` - Nested object for serialization testing

## Running Tests

### Run All Tests

```bash
mvn test
```

### Run Specific Test Category

```bash
# Unit tests only
mvn test -Dtest="*Test"

# Integration tests only
mvn test -Dtest="*IntegrationTest"

# Specific test class
mvn test -Dtest="KafkaIntegrationTest"
```

### Run with Docker

Integration tests require Docker to be running for Testcontainers.

```bash
# Ensure Docker is running
docker info

# Run tests
mvn test
```

## Test Isolation

All tests are designed to be:
- **Independent** - Each test can run in isolation
- **Deterministic** - Tests produce consistent results
- **Repeatable** - Tests can be run multiple times with same results
- **Fast** - Unit tests run quickly; integration tests use container reuse

### Isolation Strategies

1. **Unique Resources** - Each test uses unique topics/queues/exchanges
2. **Clean State** - Setup/teardown methods ensure clean state
3. **No Shared State** - Tests don't share mutable state
4. **Container Reuse** - Testcontainers reuse containers across tests for speed

## Testing Best Practices Applied

### AAA Pattern (Arrange-Act-Assert)

All tests follow the AAA pattern:

```java
@Test
void shouldPublishMessage() {
    // Arrange - Set up test data and preconditions
    TestEvent event = TestEvent.create("test");
    
    // Act - Execute the operation being tested
    publisher.publish(event, "topic").block();
    
    // Assert - Verify the expected outcome
    assertThat(receivedEvents).hasSize(1);
}
```

### Clear Test Naming

Test names clearly describe what is being tested:
- `shouldPublishMessageSuccessfully()`
- `shouldHandleNullPayload()`
- `shouldReturnHealthyStatus()`

### Reactive Testing

Uses `reactor-test` StepVerifier for testing reactive streams:

```java
StepVerifier.create(publisher.publish(event, "topic"))
    .verifyComplete();
```

### Async Testing

Uses Awaitility for testing asynchronous operations:

```java
await().atMost(Duration.ofSeconds(10))
    .untilAsserted(() -> {
        assertThat(messages).isNotEmpty();
    });
```

## Hexagonal Architecture Testing

The test suite validates the hexagonal architecture principles:

### 1. Port Testing (Interfaces)
- `EventPublisher` interface contract
- `EventConsumer` interface contract
- `MessageSerializer` interface contract

### 2. Adapter Testing (Implementations)
- Kafka adapter (KafkaEventPublisher)
- RabbitMQ adapter (RabbitMqEventPublisher)
- Application Event adapter (SpringApplicationEventPublisher)

### 3. Business Behavior Preservation

Tests verify that core business behavior remains intact regardless of infrastructure:
- Same event published to different platforms produces same result
- Switching publishers doesn't break functionality
- Serialization format is consistent across platforms

## Coverage Goals

The test suite aims for:
- **Line Coverage:** > 80%
- **Branch Coverage:** > 75%
- **Integration Coverage:** All supported messaging platforms

## Continuous Integration

Tests are designed to run in CI/CD pipelines:
- No manual setup required
- Testcontainers handles infrastructure
- Tests are self-contained
- Fast execution with container reuse

## Troubleshooting

### Docker Issues

If tests fail with Docker errors:
```bash
# Check Docker is running
docker info

# Pull required images manually
docker pull confluentinc/cp-kafka:7.5.0
docker pull rabbitmq:3.12-management-alpine
```

### Port Conflicts

Testcontainers uses random ports to avoid conflicts. If you see port binding errors:
- Ensure no other containers are using the same ports
- Restart Docker daemon

### Slow Tests

Integration tests may be slow on first run:
- Containers need to be downloaded
- Subsequent runs are faster with container reuse
- Consider running unit tests separately for faster feedback

## Future Enhancements

Planned test additions:
- [ ] Consumer unit tests
- [ ] Resilience pattern tests (circuit breaker, retry, rate limiting)
- [ ] Event listener annotation tests
- [ ] Metrics and health indicator tests
- [ ] Error handling and edge case tests

## Contributing

When adding new tests:
1. Follow the AAA pattern
2. Use descriptive test names
3. Ensure test isolation
4. Add appropriate assertions
5. Document complex test scenarios
6. Update this README if adding new test categories

