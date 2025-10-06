# EventListener Advanced Examples

This document provides comprehensive examples showcasing the advanced features of the `@EventListener` annotation in the Firefly EDA Library.

## Table of Contents

1. [Basic Event Listening](#basic-event-listening)
2. [Advanced Filtering and Routing](#advanced-filtering-and-routing)
3. [Consumer Groups and Load Balancing](#consumer-groups-and-load-balancing)
4. [Error Handling and Retry Strategies](#error-handling-and-retry-strategies)
5. [Priority-Based Event Processing](#priority-based-event-processing)
6. [Timeout and Asynchronous Processing](#timeout-and-asynchronous-processing)
7. [Multi-Platform Event Handling](#multi-platform-event-handling)
8. [SpEL Expressions in Conditions](#spel-expressions-in-conditions)
9. [Real-World Use Cases](#real-world-use-cases)

## Basic Event Listening

### Simple Event Handler

```java
@Component
@Slf4j
public class BasicEventHandler {
    
    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        consumerType = PublisherType.KAFKA
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        log.info("Received order created event: {}", event.getOrderId());
        
        return processOrder(event)
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> processOrder(OrderCreatedEvent event) {
        return Mono.fromRunnable(() -> {
            // Process the order
            System.out.println("Processing order: " + event.getOrderId());
        });
    }
}
```

### Multiple Event Types

```java
@Component
public class MultiEventHandler {
    
    @EventListener(
        destinations = "user-events",
        eventTypes = {"user.created", "user.updated", "user.deleted"}
    )
    public Mono<Void> handleUserEvents(EventEnvelope envelope) {
        return switch (envelope.eventType()) {
            case "user.created" -> handleUserCreated(envelope);
            case "user.updated" -> handleUserUpdated(envelope);
            case "user.deleted" -> handleUserDeleted(envelope);
            default -> {
                log.warn("Unknown event type: {}", envelope.eventType());
                yield envelope.acknowledge();
            }
        };
    }
    
    private Mono<Void> handleUserCreated(EventEnvelope envelope) {
        UserCreatedEvent event = (UserCreatedEvent) envelope.payload();
        return welcomeEmailService.sendWelcomeEmail(event.getEmail())
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> handleUserUpdated(EventEnvelope envelope) {
        UserUpdatedEvent event = (UserUpdatedEvent) envelope.payload();
        return userProfileService.updateProfile(event)
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> handleUserDeleted(EventEnvelope envelope) {
        UserDeletedEvent event = (UserDeletedEvent) envelope.payload();
        return userCleanupService.cleanupUserData(event.getUserId())
            .then(envelope.acknowledge());
    }
}
```

## Advanced Filtering and Routing

### Glob Pattern Matching

```java
@Component
public class PatternMatchingHandler {
    
    @EventListener(
        destinations = {"order-events", "payment-events", "shipping-events"},
        eventTypes = {"*.created", "*.completed"},  // Matches any created or completed events
        condition = "#envelope.headers['region'] == 'US'"
    )
    public Mono<Void> handleUSEventsOnly(EventEnvelope envelope) {
        log.info("Processing US event: {} of type: {}", 
                envelope.destination(), envelope.eventType());
        
        return processRegionalEvent(envelope)
            .then(envelope.acknowledge());
    }
    
    @EventListener(
        destinations = "inventory-events",
        eventTypes = "inventory.*.low",  // Matches inventory.product.low, inventory.warehouse.low, etc.
        priority = 100  // High priority for low inventory alerts
    )
    public Mono<Void> handleLowInventoryAlerts(EventEnvelope envelope) {
        return inventoryAlertService.processLowInventory(envelope.payload())
            .then(envelope.acknowledge());
    }
}
```

### Complex SpEL Conditions

```java
@Component
public class ConditionalEventHandler {
    
    @EventListener(
        destinations = "payment-events",
        eventTypes = "payment.processed",
        condition = "#envelope.payload.amount > 10000 and #envelope.headers['currency'] == 'USD'"
    )
    public Mono<Void> handleLargeUSDPayments(EventEnvelope envelope) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) envelope.payload();
        
        // Special handling for large USD payments
        return fraudDetectionService.checkLargePayment(event)
            .then(complianceService.reportLargeTransaction(event))
            .then(envelope.acknowledge());
    }
    
    @EventListener(
        destinations = "user-events",
        eventTypes = "user.login",
        condition = "#envelope.payload.loginAttempts > 3 and #envelope.payload.isSuccessful() == false"
    )
    public Mono<Void> handleSuspiciousLoginAttempts(EventEnvelope envelope) {
        UserLoginEvent event = (UserLoginEvent) envelope.payload();
        
        return securityService.flagSuspiciousActivity(event.getUserId())
            .then(notificationService.notifySecurityTeam(event))
            .then(envelope.acknowledge());
    }
    
    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        condition = "#envelope.payload.customer.vipLevel == 'GOLD' or #envelope.payload.customer.vipLevel == 'PLATINUM'"
    )
    public Mono<Void> handleVipOrderEvents(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return vipService.prioritizeOrder(event)
            .then(envelope.acknowledge());
    }
}
```

## Consumer Groups and Load Balancing

### Kafka Consumer Groups

```java
@Component
public class LoadBalancedEventHandler {
    
    @EventListener(
        destinations = "order-processing",
        eventTypes = "order.received",
        consumerType = PublisherType.KAFKA,
        groupId = "order-processors",  // All instances share this group
        autoAck = false  // Manual acknowledgment for better control
    )
    public Mono<Void> processOrderInGroup(EventEnvelope envelope) {
        OrderReceivedEvent event = (OrderReceivedEvent) envelope.payload();
        
        return orderProcessingService.processOrder(event)
            .then(envelope.acknowledge())
            .onErrorResume(error -> {
                log.error("Failed to process order: {}", event.getOrderId(), error);
                return envelope.reject(error);
            });
    }
    
    @EventListener(
        destinations = "inventory-updates",
        groupId = "inventory-sync-group",
        connectionId = "inventory-kafka",
        timeoutMs = 60000  // 1-minute timeout for complex inventory operations
    )
    public Mono<Void> synchronizeInventory(EventEnvelope envelope) {
        InventoryUpdateEvent event = (InventoryUpdateEvent) envelope.payload();
        
        return inventoryService.synchronizeStock(event)
            .timeout(Duration.ofSeconds(50))  // Timeout before the annotation timeout
            .then(envelope.acknowledge())
            .onErrorResume(TimeoutException.class, error -> {
                log.warn("Inventory sync timeout for product: {}", event.getProductId());
                return envelope.reject(error);
            });
    }
}
```

### Manual Acknowledgment Patterns

```java
@Component
public class ManualAckHandler {
    
    @EventListener(
        destinations = "financial-transactions",
        eventTypes = "transaction.created",
        autoAck = false,  // Manual acknowledgment required
        groupId = "financial-processors"
    )
    public Mono<Void> processFinancialTransaction(EventEnvelope envelope) {
        FinancialTransactionEvent event = (FinancialTransactionEvent) envelope.payload();
        
        return validateTransaction(event)
            .flatMap(isValid -> {
                if (isValid) {
                    return processValidTransaction(event)
                        .then(envelope.acknowledge());
                } else {
                    log.warn("Invalid transaction detected: {}", event.getTransactionId());
                    return envelope.reject(new ValidationException("Invalid transaction"));
                }
            })
            .onErrorResume(error -> {
                log.error("Error processing transaction: {}", event.getTransactionId(), error);
                return envelope.reject(error);
            });
    }
    
    private Mono<Boolean> validateTransaction(FinancialTransactionEvent event) {
        return transactionValidator.validate(event);
    }
    
    private Mono<Void> processValidTransaction(FinancialTransactionEvent event) {
        return transactionProcessor.process(event);
    }
}
```

## Error Handling and Retry Strategies

### Comprehensive Error Handling

```java
@Component
@Slf4j
public class ErrorHandlingExamples {
    
    @EventListener(
        destinations = "payment-processing",
        eventTypes = "payment.initiated",
        errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
        maxRetries = 5,
        retryDelayMs = 2000  // 2-second delay between retries
    )
    public Mono<Void> processPaymentWithRetries(EventEnvelope envelope) {
        PaymentInitiatedEvent event = (PaymentInitiatedEvent) envelope.payload();
        
        return paymentGateway.processPayment(event)
            .then(envelope.acknowledge())
            .onErrorResume(PaymentTemporaryException.class, error -> {
                log.warn("Temporary payment error, will retry: {}", error.getMessage());
                return envelope.reject(error);  // Will trigger retry
            })
            .onErrorResume(PaymentPermanentException.class, error -> {
                log.error("Permanent payment error, not retrying: {}", error.getMessage());
                return handlePermanentFailure(envelope, error);
            });
    }
    
    @EventListener(
        destinations = "order-fulfillment",
        eventTypes = "order.fulfill",
        errorStrategy = ErrorHandlingStrategy.DEAD_LETTER,
        maxRetries = 3
    )
    public Mono<Void> fulfillOrderWithDLQ(EventEnvelope envelope) {
        OrderFulfillEvent event = (OrderFulfillEvent) envelope.payload();
        
        return fulfillmentService.fulfillOrder(event)
            .then(envelope.acknowledge())
            .onErrorResume(InventoryException.class, error -> {
                // Don't retry inventory errors, send to DLQ immediately
                log.error("Inventory error for order {}: {}", event.getOrderId(), error.getMessage());
                return envelope.reject(error);
            });
    }
    
    @EventListener(
        destinations = "notifications",
        eventTypes = "notification.send",
        errorStrategy = ErrorHandlingStrategy.CUSTOM
    )
    public Mono<Void> sendNotificationWithCustomError(EventEnvelope envelope) {
        NotificationEvent event = (NotificationEvent) envelope.payload();
        
        return notificationService.send(event)
            .then(envelope.acknowledge())
            .onErrorResume(error -> customErrorHandler(envelope, event, error));
    }
    
    private Mono<Void> customErrorHandler(EventEnvelope envelope, NotificationEvent event, Throwable error) {
        return switch (error) {
            case EmailDeliveryException emailError -> {
                log.warn("Email delivery failed, trying SMS fallback");
                yield smsService.sendSMS(event.getUserId(), event.getMessage())
                    .then(envelope.acknowledge());
            }
            case RateLimitException rateLimitError -> {
                log.info("Rate limited, scheduling for later");
                yield scheduleForLater(envelope, Duration.ofMinutes(5));
            }
            case UserNotFoundException userError -> {
                log.warn("User not found, acknowledging and skipping: {}", event.getUserId());
                yield envelope.acknowledge();  // Don't retry for non-existent users
            }
            default -> {
                log.error("Unexpected notification error", error);
                yield envelope.reject(error);
            }
        };
    }
    
    private Mono<Void> handlePermanentFailure(EventEnvelope envelope, Throwable error) {
        return deadLetterService.store(envelope, error)
            .then(envelope.acknowledge());  // Acknowledge to prevent reprocessing
    }
    
    private Mono<Void> scheduleForLater(EventEnvelope envelope, Duration delay) {
        return schedulerService.schedule(envelope, delay)
            .then(envelope.acknowledge());
    }
}
```

## Priority-Based Event Processing

### Priority Queues and Processing

```java
@Component
public class PriorityEventHandlers {
    
    // Critical system events - highest priority
    @EventListener(
        destinations = "system-events",
        eventTypes = {"system.shutdown", "system.emergency"},
        priority = 1000,
        async = false  // Process synchronously for immediate handling
    )
    public Mono<Void> handleCriticalSystemEvents(EventEnvelope envelope) {
        SystemEvent event = (SystemEvent) envelope.payload();
        
        log.error("CRITICAL: System event received: {}", event.getEventType());
        
        return emergencyHandler.handle(event)
            .then(envelope.acknowledge());
    }
    
    // High priority business events
    @EventListener(
        destinations = "business-events",
        eventTypes = {"order.urgent", "payment.critical", "fraud.detected"},
        priority = 500,
        timeoutMs = 10000
    )
    public Mono<Void> handleHighPriorityBusinessEvents(EventEnvelope envelope) {
        return switch (envelope.eventType()) {
            case "order.urgent" -> handleUrgentOrder(envelope);
            case "payment.critical" -> handleCriticalPayment(envelope);
            case "fraud.detected" -> handleFraudDetection(envelope);
            default -> envelope.acknowledge();
        };
    }
    
    // Normal priority events
    @EventListener(
        destinations = {"order-events", "user-events"},
        eventTypes = {"*.created", "*.updated"},
        priority = 100
    )
    public Mono<Void> handleNormalPriorityEvents(EventEnvelope envelope) {
        return standardEventProcessor.process(envelope)
            .then(envelope.acknowledge());
    }
    
    // Low priority events (analytics, logging, etc.)
    @EventListener(
        destinations = "analytics-events",
        eventTypes = {"user.click", "page.view", "search.query"},
        priority = 1,
        async = true
    )
    public Mono<Void> handleLowPriorityEvents(EventEnvelope envelope) {
        // Process asynchronously without blocking higher priority events
        return analyticsService.recordEvent(envelope.payload())
            .subscribeOn(Schedulers.boundedElastic())
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> handleUrgentOrder(EventEnvelope envelope) {
        UrgentOrderEvent event = (UrgentOrderEvent) envelope.payload();
        
        return urgentOrderService.prioritizeOrder(event)
            .then(notificationService.alertOperations("Urgent order: " + event.getOrderId()))
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> handleCriticalPayment(EventEnvelope envelope) {
        CriticalPaymentEvent event = (CriticalPaymentEvent) envelope.payload();
        
        return paymentService.expeditePayment(event)
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> handleFraudDetection(EventEnvelope envelope) {
        FraudDetectedEvent event = (FraudDetectedEvent) envelope.payload();
        
        return fraudService.blockAccount(event.getUserId())
            .then(securityService.notifySecurityTeam(event))
            .then(envelope.acknowledge());
    }
}
```

## Timeout and Asynchronous Processing

### Timeout Configurations

```java
@Component
public class TimeoutExamples {
    
    @EventListener(
        destinations = "long-running-tasks",
        eventTypes = "task.start",
        timeoutMs = 300000,  // 5-minute timeout
        async = true
    )
    public Mono<Void> handleLongRunningTask(EventEnvelope envelope) {
        LongRunningTaskEvent event = (LongRunningTaskEvent) envelope.payload();
        
        return longRunningService.executeTask(event)
            .timeout(Duration.ofMinutes(4))  // Internal timeout slightly less than annotation
            .then(envelope.acknowledge())
            .onErrorResume(TimeoutException.class, error -> {
                log.warn("Task timeout for: {}", event.getTaskId());
                return taskService.markAsTimeout(event.getTaskId())
                    .then(envelope.acknowledge());
            });
    }
    
    @EventListener(
        destinations = "quick-operations",
        eventTypes = "operation.execute",
        timeoutMs = 5000,  // 5-second timeout
        async = false
    )
    public Mono<Void> handleQuickOperations(EventEnvelope envelope) {
        QuickOperationEvent event = (QuickOperationEvent) envelope.payload();
        
        return quickOperationService.execute(event)
            .timeout(Duration.ofSeconds(4))
            .then(envelope.acknowledge())
            .onErrorResume(TimeoutException.class, error -> {
                log.error("Quick operation timeout: {}", event.getOperationId());
                return envelope.reject(error);
            });
    }
    
    // No timeout - for fire-and-forget operations
    @EventListener(
        destinations = "background-tasks",
        eventTypes = "cleanup.*",
        timeoutMs = 0,  // No timeout
        async = true
    )
    public Mono<Void> handleBackgroundCleanup(EventEnvelope envelope) {
        CleanupEvent event = (CleanupEvent) envelope.payload();
        
        return cleanupService.performCleanup(event)
            .subscribeOn(Schedulers.boundedElastic())  // Run on background thread
            .then(envelope.acknowledge())
            .doOnError(error -> log.error("Cleanup failed", error))
            .onErrorResume(error -> envelope.acknowledge());  // Always ack cleanup events
    }
}
```

## Multi-Platform Event Handling

### Cross-Platform Event Processing

```java
@Component
public class MultiPlatformHandler {
    
    // Kafka for high-throughput events
    @EventListener(
        destinations = "kafka-order-events",
        eventTypes = "order.*",
        consumerType = PublisherType.KAFKA,
        connectionId = "primary-kafka",
        groupId = "order-processors"
    )
    public Mono<Void> handleKafkaOrders(EventEnvelope envelope) {
        return orderService.processKafkaOrder(envelope.payload())
            .then(envelope.acknowledge());
    }
    
    // RabbitMQ for reliable messaging
    @EventListener(
        destinations = "rabbitmq-payments",
        eventTypes = "payment.*",
        consumerType = PublisherType.RABBITMQ,
        connectionId = "payments-rabbitmq",
        errorStrategy = ErrorHandlingStrategy.DEAD_LETTER,
        maxRetries = 3
    )
    public Mono<Void> handleRabbitMQPayments(EventEnvelope envelope) {
        return paymentService.processRabbitMQPayment(envelope.payload())
            .then(envelope.acknowledge());
    }
    
    
    // Spring Application Events for internal communication
    @EventListener(
        eventTypes = {"internal.*", "cache.*"},
        consumerType = PublisherType.APPLICATION_EVENT
    )
    public Mono<Void> handleInternalEvents(EventEnvelope envelope) {
        return internalEventProcessor.process(envelope)
            .then(envelope.acknowledge());
    }
    
    // Auto-detection based on configuration
    @EventListener(
        destinations = "auto-events",
        eventTypes = "auto.*",
        consumerType = PublisherType.AUTO  // Will use the best available publisher
    )
    public Mono<Void> handleAutoEvents(EventEnvelope envelope) {
        log.info("Processing auto-detected event from: {}", envelope.source());
        
        return autoEventProcessor.process(envelope)
            .then(envelope.acknowledge());
    }
}
```

## SpEL Expressions in Conditions

### Advanced SpEL Usage

```java
@Component
public class SpELExamples {
    
    @EventListener(
        destinations = "user-events",
        eventTypes = "user.action",
        condition = "#envelope.payload.user.age >= 18 and #envelope.payload.user.country in {'US', 'CA', 'UK'}"
    )
    public Mono<Void> handleAdultUserActions(EventEnvelope envelope) {
        UserActionEvent event = (UserActionEvent) envelope.payload();
        return adultUserService.processAction(event)
            .then(envelope.acknowledge());
    }
    
    @EventListener(
        destinations = "transaction-events",
        eventTypes = "transaction.created",
        condition = "(#envelope.payload.amount > 1000 and #envelope.headers['source'] == 'mobile') or " +
                   "(#envelope.payload.amount > 5000 and #envelope.headers['source'] == 'web')"
    )
    public Mono<Void> handleHighValueTransactions(EventEnvelope envelope) {
        TransactionEvent event = (TransactionEvent) envelope.payload();
        return highValueTransactionService.process(event)
            .then(envelope.acknowledge());
    }
    
    @EventListener(
        destinations = "product-events",
        eventTypes = "product.updated",
        condition = "#envelope.payload.category.name matches 'Electronics|Books|Clothing' and " +
                   "#envelope.payload.priceChange != null and " +
                   "#envelope.payload.priceChange.percentChange > 10"
    )
    public Mono<Void> handleSignificantPriceChanges(EventEnvelope envelope) {
        ProductUpdatedEvent event = (ProductUpdatedEvent) envelope.payload();
        return priceAlertService.notifyPriceChange(event)
            .then(envelope.acknowledge());
    }
    
    @EventListener(
        destinations = "system-metrics",
        eventTypes = "metric.recorded",
        condition = "#envelope.payload.metricName == 'cpu_usage' and " +
                   "#envelope.payload.value > 80.0 and " +
                   "#envelope.headers['environment'] == 'production'"
    )
    public Mono<Void> handleHighCPUUsage(EventEnvelope envelope) {
        MetricEvent event = (MetricEvent) envelope.payload();
        return alertingService.sendHighCPUAlert(event)
            .then(envelope.acknowledge());
    }
    
    // Using custom SpEL functions
    @EventListener(
        destinations = "order-events",
        eventTypes = "order.placed",
        condition = "@orderValidator.isValid(#envelope.payload) and " +
                   "@riskAssessment.assessRisk(#envelope.payload) < 0.5"
    )
    public Mono<Void> handleLowRiskOrders(EventEnvelope envelope) {
        OrderPlacedEvent event = (OrderPlacedEvent) envelope.payload();
        return fastTrackService.processOrder(event)
            .then(envelope.acknowledge());
    }
}
```

## Real-World Use Cases

### E-commerce Order Processing

```java
@Component
@RequiredArgsConstructor
public class EcommerceOrderHandler {
    
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;
    private final NotificationService notificationService;
    
    // Handle order creation with inventory check
    @EventListener(
        destinations = "order-events",
        eventTypes = "order.created",
        priority = 100,
        timeoutMs = 30000,
        errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY,
        maxRetries = 3
    )
    public Mono<Void> handleOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.payload();
        
        return inventoryService.reserveItems(event.getOrderId(), event.getItems())
            .flatMap(reserved -> {
                if (reserved) {
                    return paymentService.authorizePayment(event.getPaymentInfo())
                        .then(envelope.acknowledge());
                } else {
                    return notificationService.notifyOutOfStock(event.getCustomerId(), event.getItems())
                        .then(envelope.acknowledge()); // Acknowledge even if out of stock
                }
            })
            .onErrorResume(error -> {
                log.error("Failed to process order: {}", event.getOrderId(), error);
                return inventoryService.releaseReservation(event.getOrderId())
                    .then(envelope.reject(error));
            });
    }
    
    // Handle payment confirmation
    @EventListener(
        destinations = "payment-events",
        eventTypes = "payment.confirmed",
        condition = "#envelope.payload.status == 'SUCCESS'",
        priority = 200
    )
    public Mono<Void> handlePaymentConfirmed(EventEnvelope envelope) {
        PaymentConfirmedEvent event = (PaymentConfirmedEvent) envelope.payload();
        
        return shippingService.createShippingLabel(event.getOrderId())
            .then(notificationService.sendOrderConfirmation(event.getCustomerId()))
            .then(envelope.acknowledge());
    }
    
    // Handle failed payments
    @EventListener(
        destinations = "payment-events",
        eventTypes = "payment.failed",
        priority = 200,
        async = true
    )
    public Mono<Void> handlePaymentFailed(EventEnvelope envelope) {
        PaymentFailedEvent event = (PaymentFailedEvent) envelope.payload();
        
        return inventoryService.releaseReservation(event.getOrderId())
            .then(notificationService.sendPaymentFailureNotification(event.getCustomerId()))
            .then(envelope.acknowledge());
    }
}
```

### Real-Time Analytics Processing

```java
@Component
@RequiredArgsConstructor
public class AnalyticsEventHandler {
    
    private final AnalyticsService analyticsService;
    private final RealtimeMetricsService metricsService;
    private final AlertingService alertingService;
    
    // High-frequency user interaction events
    @EventListener(
        destinations = "user-interactions",
        eventTypes = {"page.view", "button.click", "form.submit"},
        groupId = "analytics-processors",
        priority = 1,  // Low priority to not block business events
        async = true
    )
    public Mono<Void> processUserInteractions(EventEnvelope envelope) {
        UserInteractionEvent event = (UserInteractionEvent) envelope.payload();
        
        return analyticsService.recordInteraction(event)
            .subscribeOn(Schedulers.boundedElastic())
            .then(envelope.acknowledge())
            .onErrorResume(error -> {
                // Don't fail the pipeline for analytics errors
                log.warn("Analytics processing failed", error);
                return envelope.acknowledge();
            });
    }
    
    // Critical business metrics
    @EventListener(
        destinations = "business-metrics",
        eventTypes = "metric.*",
        condition = "#envelope.payload.category == 'REVENUE' or " +
                   "#envelope.payload.category == 'CONVERSION'",
        priority = 50,
        timeoutMs = 10000
    )
    public Mono<Void> processBusinessMetrics(EventEnvelope envelope) {
        BusinessMetricEvent event = (BusinessMetricEvent) envelope.payload();
        
        return metricsService.updateMetric(event)
            .then(checkForAlerts(event))
            .then(envelope.acknowledge());
    }
    
    // Anomaly detection
    @EventListener(
        destinations = "metrics",
        eventTypes = "anomaly.detected",
        priority = 500,  // High priority for anomalies
        async = false    // Process synchronously for immediate alerts
    )
    public Mono<Void> handleAnomalies(EventEnvelope envelope) {
        AnomalyDetectedEvent event = (AnomalyDetectedEvent) envelope.payload();
        
        return alertingService.sendAnomalyAlert(event)
            .then(metricsService.flagAnomaly(event))
            .then(envelope.acknowledge());
    }
    
    private Mono<Void> checkForAlerts(BusinessMetricEvent event) {
        return Mono.fromRunnable(() -> {
            if (event.getValue() < event.getThreshold()) {
                alertingService.sendBusinessAlert(event);
            }
        });
    }
}
```

### Financial Transaction Processing

```java
@Component
@RequiredArgsConstructor
public class FinancialTransactionHandler {
    
    private final TransactionValidator transactionValidator;
    private final FraudDetectionService fraudService;
    private final ComplianceService complianceService;
    private final LedgerService ledgerService;
    
    // High-value transaction processing
    @EventListener(
        destinations = "transactions",
        eventTypes = "transaction.initiated",
        condition = "#envelope.payload.amount > 10000",
        priority = 1000,  // Highest priority
        errorStrategy = ErrorHandlingStrategy.DEAD_LETTER,
        maxRetries = 1,   // Minimal retries for financial data
        timeoutMs = 60000, // 1-minute timeout
        autoAck = false   // Manual acknowledgment for financial integrity
    )
    public Mono<Void> processHighValueTransaction(EventEnvelope envelope) {
        HighValueTransactionEvent event = (HighValueTransactionEvent) envelope.payload();
        
        return transactionValidator.validate(event)
            .flatMap(valid -> {
                if (!valid) {
                    return envelope.reject(new ValidationException("Invalid transaction"));
                }
                
                return fraudService.checkTransaction(event)
                    .flatMap(fraudResult -> {
                        if (fraudResult.isFraudulent()) {
                            return fraudService.blockTransaction(event)
                                .then(envelope.acknowledge());
                        }
                        
                        return complianceService.checkCompliance(event)
                            .flatMap(compliant -> {
                                if (!compliant) {
                                    return complianceService.reportSuspiciousActivity(event)
                                        .then(envelope.acknowledge());
                                }
                                
                                return ledgerService.recordTransaction(event)
                                    .then(envelope.acknowledge());
                            });
                    });
            })
            .onErrorResume(error -> {
                log.error("High-value transaction processing failed: {}", event.getTransactionId(), error);
                return envelope.reject(error);
            });
    }
    
    // Regular transaction processing
    @EventListener(
        destinations = "transactions",
        eventTypes = "transaction.initiated",
        condition = "#envelope.payload.amount <= 10000",
        groupId = "regular-transaction-processors",
        priority = 100,
        timeoutMs = 30000
    )
    public Mono<Void> processRegularTransaction(EventEnvelope envelope) {
        RegularTransactionEvent event = (RegularTransactionEvent) envelope.payload();
        
        return transactionValidator.validate(event)
            .flatMap(valid -> {
                if (!valid) {
                    return envelope.reject(new ValidationException("Invalid transaction"));
                }
                
                return ledgerService.recordTransaction(event)
                    .then(envelope.acknowledge());
            });
    }
    
    // Transaction settlement processing
    @EventListener(
        destinations = "settlement-events",
        eventTypes = "settlement.scheduled",
        priority = 300,
        async = false,  // Synchronous processing for settlements
        timeoutMs = 120000  // 2-minute timeout for settlement operations
    )
    public Mono<Void> processSettlement(EventEnvelope envelope) {
        SettlementScheduledEvent event = (SettlementScheduledEvent) envelope.payload();
        
        return ledgerService.processSettlement(event)
            .then(complianceService.recordSettlement(event))
            .then(envelope.acknowledge());
    }
}
```

## Configuration Examples

### Application Configuration

```yaml
# application.yml
firefly:
  eda:
    enabled: true
    default-publisher-type: KAFKA
    metrics-enabled: true
    health-enabled: true
    
    publishers:
      kafka:
        primary-kafka:
          enabled: true
          bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
          default-topic: events
          properties:
            acks: all
            retries: 3
            max.in.flight.requests.per.connection: 1
        
        inventory-kafka:
          enabled: true
          bootstrap-servers: ${INVENTORY_KAFKA_BROKERS:localhost:9093}
          default-topic: inventory-events
          
      rabbitmq:
        payments-rabbitmq:
          enabled: true
          host: ${RABBITMQ_HOST:localhost}
          port: ${RABBITMQ_PORT:5672}
          username: ${RABBITMQ_USER:guest}
          password: ${RABBITMQ_PASS:guest}
          default-exchange: payment-exchange
          

logging:
  level:
    com.firefly.common.eda: DEBUG
```

### Spring Configuration

```java
@Configuration
@EnableEda
public class EdaConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "app.environment", havingValue = "production")
    public EventFilter productionEventFilter() {
        return EventFilter.composite(
            EventFilter.byHeader("environment", "production"),
            EventFilter.byPredicate(envelope -> 
                !envelope.eventType().startsWith("test."))
        );
    }
    
    @Bean
    public TaskExecutor eventProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("eda-event-");
        executor.initialize();
        return executor;
    }
    
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void logAllEvents(EventReceivedEvent event) {
        log.debug("Event received: {} from {}", 
                 event.getEventType(), event.getDestination());
    }
}
```

This comprehensive guide demonstrates the full power and flexibility of the `@EventListener` annotation in the Firefly EDA Library. The examples show how to handle complex event processing scenarios while maintaining high performance, reliability, and maintainability.