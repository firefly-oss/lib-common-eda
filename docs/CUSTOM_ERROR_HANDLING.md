# Custom Error Handling

The Firefly EDA library supports custom error handling strategies that allow you to implement your own error handling logic for event listeners.

## Overview

When you set the error handling strategy to `CUSTOM` in your `@EventListener` annotation, the library will delegate error handling to registered custom error handlers instead of using the built-in strategies.

## Basic Usage

### 1. Annotate Your Event Listener

```java
@EventListener(
    eventType = OrderCreatedEvent.class,
    errorHandling = ErrorHandlingStrategy.CUSTOM
)
public void handleOrderCreated(OrderCreatedEvent event) {
    // Your event handling logic
    if (someCondition) {
        throw new RuntimeException("Something went wrong");
    }
}
```

### 2. Implement a Custom Error Handler

```java
@Component
public class MyCustomErrorHandler implements CustomErrorHandler {

    @Override
    public Mono<Void> handleError(Object event, Map<String, Object> headers, 
                                 Throwable error, String listenerMethod) {
        return Mono.fromRunnable(() -> {
            // Your custom error handling logic
            log.error("Custom error handling for {}: {}", listenerMethod, error.getMessage());
            
            // Send notifications, create tickets, etc.
            notificationService.sendAlert(error, event);
        });
    }

    @Override
    public String getHandlerName() {
        return "my-custom-handler";
    }

    @Override
    public boolean canHandle(Class<? extends Throwable> errorType) {
        // Only handle specific error types
        return RuntimeException.class.isAssignableFrom(errorType);
    }

    @Override
    public int getPriority() {
        return 100; // Higher numbers = higher priority
    }
}
```

## Built-in Custom Error Handlers

The library includes several built-in custom error handlers that you can enable:

### Notification Error Handler

Sends notifications for critical errors.

**Configuration:**
```yaml
firefly:
  eda:
    error:
      notification:
        enabled: true
```

**Features:**
- Handles `RuntimeException`, `IllegalStateException`, and `SecurityException`
- Differentiates between critical and warning alerts
- High priority (100)

### Metrics Error Handler

Records error metrics for monitoring and observability.

**Configuration:**
```yaml
firefly:
  eda:
    error:
      metrics:
        enabled: true  # Default: true
```

**Features:**
- Records error counts by listener method and error type
- Records error processing duration
- Integrates with Micrometer metrics
- Medium priority (50)

## Advanced Features

### Error Type Filtering

You can implement selective error handling based on error types:

```java
@Override
public boolean canHandle(Class<? extends Throwable> errorType) {
    // Only handle database-related errors
    return SQLException.class.isAssignableFrom(errorType) ||
           DataAccessException.class.isAssignableFrom(errorType);
}
```

### Priority-based Execution

Multiple custom error handlers are executed in priority order (highest first):

```java
@Override
public int getPriority() {
    return 200; // Very high priority
}
```

### Programmatic Registration

You can register error handlers programmatically:

```java
@Autowired
private CustomErrorHandlerRegistry errorHandlerRegistry;

public void registerMyHandler() {
    CustomErrorHandler handler = new MyDynamicErrorHandler();
    errorHandlerRegistry.registerHandler(handler);
}
```

## Error Handler Lifecycle

1. **Discovery**: Custom error handlers are automatically discovered as Spring beans
2. **Registration**: Handlers are registered and sorted by priority during startup
3. **Execution**: When a CUSTOM error strategy is triggered:
   - All applicable handlers (based on `canHandle()`) are identified
   - Handlers are executed in priority order
   - Each handler runs independently (failures don't affect others)
   - All handlers complete before the error handling finishes

## Best Practices

### 1. Handle Errors Gracefully

```java
@Override
public Mono<Void> handleError(Object event, Map<String, Object> headers, 
                             Throwable error, String listenerMethod) {
    return Mono.fromRunnable(() -> {
        try {
            // Your error handling logic
            performErrorHandling(event, error);
        } catch (Exception e) {
            // Don't let handler errors propagate
            log.warn("Error handler failed", e);
        }
    });
}
```

### 2. Use Appropriate Priorities

- **Critical handlers** (notifications, alerts): 100-200
- **Monitoring handlers** (metrics, logging): 50-99
- **Cleanup handlers** (resource cleanup): 1-49

### 3. Implement Efficient Error Type Checking

```java
@Override
public boolean canHandle(Class<? extends Throwable> errorType) {
    // Use efficient type checking
    return HANDLED_TYPES.stream()
        .anyMatch(type -> type.isAssignableFrom(errorType));
}

private static final Set<Class<? extends Throwable>> HANDLED_TYPES = Set.of(
    RuntimeException.class,
    IllegalStateException.class,
    SecurityException.class
);
```

### 4. Provide Meaningful Handler Names

```java
@Override
public String getHandlerName() {
    return "order-processing-error-handler";
}
```

## Configuration

### Enable/Disable Custom Error Handling

```yaml
firefly:
  eda:
    error:
      custom:
        enabled: true  # Default: true
```

### Configure Built-in Handlers

```yaml
firefly:
  eda:
    error:
      notification:
        enabled: true
        critical-threshold: ERROR
      metrics:
        enabled: true
        detailed-timing: true
```

## Troubleshooting

### No Custom Handlers Available

If you see this warning:
```
CUSTOM error strategy specified but no custom error handlers available, falling back to LOG_AND_CONTINUE
```

**Solutions:**
1. Ensure your custom error handler is annotated with `@Component`
2. Verify the handler is in a package scanned by Spring
3. Check that the handler implements `CustomErrorHandler` correctly

### Handler Not Executing

**Check:**
1. The `canHandle()` method returns `true` for your error type
2. The handler is properly registered (check logs during startup)
3. The error handling strategy is set to `CUSTOM`

### Performance Issues

**Optimize:**
1. Keep error handling logic lightweight
2. Use async operations for heavy processing
3. Implement efficient error type checking
4. Consider handler priorities to avoid unnecessary processing
