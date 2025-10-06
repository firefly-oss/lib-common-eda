/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.eda.publisher;

import com.firefly.common.eda.annotation.PublisherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * A wrapper for EventPublisher that provides a custom default destination.
 * <p>
 * This wrapper allows overriding the default destination configured in application
 * properties, enabling dynamic topic/destination selection at runtime while
 * maintaining backward compatibility with existing code.
 * <p>
 * When a destination is explicitly provided to the publish methods, it takes
 * precedence over both the wrapper's custom default and the underlying publisher's
 * configured default.
 */
@RequiredArgsConstructor
@Slf4j
public class DestinationAwarePublisher implements EventPublisher {

    private final EventPublisher delegate;
    private final String customDefaultDestination;

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        String effectiveDestination = resolveDestination(destination);
        log.debug("Publishing with destination override: requested={}, effective={}", 
                 destination, effectiveDestination);
        return delegate.publish(event, effectiveDestination, headers);
    }

    @Override
    public Mono<Void> publish(Object event, String destination) {
        String effectiveDestination = resolveDestination(destination);
        log.debug("Publishing with destination override: requested={}, effective={}", 
                 destination, effectiveDestination);
        return delegate.publish(event, effectiveDestination);
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public PublisherType getPublisherType() {
        return delegate.getPublisherType();
    }

    @Override
    public String getDefaultDestination() {
        // Return the custom default destination if set, otherwise delegate
        return customDefaultDestination != null ? customDefaultDestination : delegate.getDefaultDestination();
    }

    @Override
    public Mono<PublisherHealth> getHealth() {
        return delegate.getHealth()
                .map(health -> PublisherHealth.builder()
                        .publisherType(health.getPublisherType())
                        .available(health.isAvailable())
                        .status(health.getStatus())
                        .connectionId(health.getConnectionId())
                        .details(Map.of(
                                "delegateHealth", health.getDetails(),
                                "customDefaultDestination", customDefaultDestination != null ? customDefaultDestination : "none",
                                "effectiveDefaultDestination", getDefaultDestination()
                        ))
                        .build());
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * Resolves the effective destination to use for publishing.
     * <p>
     * Priority order:
     * 1. Explicitly provided destination (highest priority)
     * 2. Custom default destination from this wrapper
     * 3. Default destination from the underlying publisher
     *
     * @param requestedDestination the destination explicitly requested (may be null)
     * @return the effective destination to use
     */
    private String resolveDestination(String requestedDestination) {
        // Check for explicit destination first
        if (isValidDestination(requestedDestination)) {
            log.trace("Using explicit destination: {}", requestedDestination);
            return requestedDestination.trim();
        }

        // Fall back to custom default destination
        if (isValidDestination(customDefaultDestination)) {
            log.trace("Using custom default destination: {}", customDefaultDestination);
            return customDefaultDestination.trim();
        }

        // Final fallback to delegate's default destination
        String delegateDefault = delegate.getDefaultDestination();
        log.trace("Using delegate default destination: {}", delegateDefault);
        return delegateDefault;
    }

    /**
     * Validates if a destination string is valid (not null, not empty, not just whitespace).
     *
     * @param destination the destination to validate
     * @return true if the destination is valid
     */
    private boolean isValidDestination(String destination) {
        return destination != null && !destination.trim().isEmpty();
    }

    /**
     * Gets the underlying delegate publisher.
     *
     * @return the delegate publisher
     */
    public EventPublisher getDelegate() {
        return delegate;
    }

    /**
     * Gets the custom default destination configured for this wrapper.
     *
     * @return the custom default destination, or null if none is set
     */
    public String getCustomDefaultDestination() {
        return customDefaultDestination;
    }
}
