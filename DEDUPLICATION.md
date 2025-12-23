# EDA Consumer Deduplication

This document describes the cache-backed deduplication guard built into the Firefly Common EDA library to prevent executing event listeners more than once for the same event.

## Overview

The deduplication guard is applied centrally in the EventListenerProcessor before invoking any `@EventListener` methods. It uses Firefly's reactive cache manager (`FireflyCacheManager`) to record a short-lived marker for processed events. If another event with the same identity arrives within the configured TTL, listeners are skipped.

The system is designed to be safe-by-default and fail-open: when the cache is not available or an error occurs, events are processed normally (no drops), and a warning is logged.

## How it works

1. A unique deduplication key is built per event using the first available identifier in this order:
   - Header variants: `transaction-id`, `transactionId`, `x-transaction-id`, `message_id`, `correlation_id`, `event_id`, `event-id`.
   - Provider-specific tuple if headers are missing:
     - Kafka: `kafka.topic` + `kafka.partition` + `kafka.offset`
     - RabbitMQ: `amqp_receivedExchange` + `amqp_receivedRoutingKey` + `amqp_deliveryTag`
   - Fallback: SHA-256 of `event.getClass().getName() + ":" + String.valueOf(event)` (best-effort).
2. The cache is queried for the key. If present, the event is considered a duplicate and is skipped.
3. If absent, a marker value ("1") is stored with the configured TTL, and listeners are executed.
4. Any cache error results in processing the event anyway (fail-open).

## Configuration

Configure both the cache library and the EDA deduplication properties.

### Cache library (Firefly Common Cache)

```yaml
firefly:
  cache:
    enabled: true                     # Enable/disable the cache library
    default-cache-type: CAFFEINE      # CAFFEINE, REDIS, AUTO, NOOP
    default-cache-name: default
    default-serialization-format: json
    default-timeout: PT5S             # Default operation timeout
```

### EDA consumer deduplication

```yaml
firefly:
  eda:
    consumer:
      dedup:
        enabled: true                 # Guard enabled (default: false)
        ttl: PT10M                    # Marker TTL (window to suppress duplicates)
        key-prefix: "eda:dedup:"       # Cache key prefix
```

## Runtime behavior

- Optional dependency injection: the library injects `FireflyCacheManager` via `ObjectProvider` so the dedup guard only activates when a cache manager bean is present. If it is missing or disabled, dedup gracefully bypasses processing (fail-open).
- Scope of guarantees:
  - CAFFEINE (in-memory) ensures deduplication within a single JVM instance.
  - For distributed, multi-instance deduplication, use REDIS (or any backend that supports atomic set-if-absent) and choose a TTL that matches your expected duplicate window.
- Zero database requirement: the mechanism relies solely on the cache.

## Integration details

- The guard is applied automatically by `EventListenerProcessor.processEvent(...)`.
- Internal API (for reference/testing):
  - `DeduplicationService.tryAcquire(Object event, Map<String, Object> headers) : Mono<Boolean>`
    - Returns `true` to proceed with processing; `false` when the event is a duplicate within TTL.

## Troubleshooting

- Events still processed twice:
  - Ensure a stable identifier is present in headers (e.g., `transaction-id`). If none is available, the system falls back to provider tuples or a best-effort hash.
  - Verify cache is enabled and reachable: `firefly.cache.enabled=true` and correct `default-cache-type`.
  - Increase `ttl` if duplicates occur outside the current window.
- Distributed environments:
  - Prefer `REDIS` as `default-cache-type` for cross-node deduplication.
- Observability:
  - Look for log lines prefixed with `[Dedup]` for insights on key building and fail-open behavior.

## Security and performance notes

- Keys include only header-derived identifiers or hashed fallbacks; payload content is not stored.
- Using a shared external cache (e.g., Redis) introduces network IO; adjust timeouts accordingly.
- Choose a TTL that balances memory usage and duplicate suppression needs.

---

This deduplication system is part of the Firefly Common EDA library and is available for all consumer implementations (Kafka, RabbitMQ, and Spring Application Events). It is disabled by default and must be explicitly enabled via configuration.
