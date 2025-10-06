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

package com.firefly.common.eda.filter;

import com.firefly.common.eda.consumer.EventConsumer;
import com.firefly.common.eda.event.EventEnvelope;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Composite event filter that combines multiple filters using logical operators.
 * <p>
 * Supports AND and OR logic for combining filters. This allows for complex
 * filtering scenarios such as:
 * <ul>
 *   <li>Messages from specific topics AND containing certain headers</li>
 *   <li>Messages of certain event types OR from specific destinations</li>
 *   <li>Complex nested combinations using multiple composite filters</li>
 * </ul>
 */
public class CompositeEventFilter implements EventFilter {

    /**
     * Logic type for combining filters.
     */
    public enum LogicType {
        AND, OR
    }

    private final LogicType logicType;
    private final List<EventFilter> filters;

    public CompositeEventFilter(LogicType logicType, EventFilter... filters) {
        this.logicType = logicType;
        this.filters = Arrays.asList(filters);
    }

    public CompositeEventFilter(LogicType logicType, List<EventFilter> filters) {
        this.logicType = logicType;
        this.filters = filters;
    }

    @Override
    public boolean accept(String messageBody, Map<String, Object> headers) {
        if (filters == null || filters.isEmpty()) {
            return true; // Empty filter list accepts all messages
        }

        return switch (logicType) {
            case AND -> filters.stream().allMatch(filter -> filter.accept(messageBody, headers));
            case OR -> filters.stream().anyMatch(filter -> filter.accept(messageBody, headers));
        };
    }

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (filters == null || filters.isEmpty()) {
            return true; // Empty filter list accepts all messages
        }

        return switch (logicType) {
            case AND -> filters.stream().allMatch(filter -> filter.matches(envelope));
            case OR -> filters.stream().anyMatch(filter -> filter.matches(envelope));
        };
    }

    @Override
    public String getDescription() {
        String filterDescriptions = filters.stream()
                .map(EventFilter::getDescription)
                .collect(Collectors.joining(" " + logicType.name() + " "));
        
        return "CompositeFilter[" + filterDescriptions + "]";
    }

    /**
     * Gets the logic type used by this composite filter.
     */
    public LogicType getLogicType() {
        return logicType;
    }

    /**
     * Gets the list of filters being combined.
     */
    public List<EventFilter> getFilters() {
        return filters;
    }

    /**
     * Adds additional filters to this composite filter.
     * Note: This creates a new filter instance.
     */
    public CompositeEventFilter addFilters(EventFilter... additionalFilters) {
        List<EventFilter> combined = Arrays.asList(additionalFilters);
        combined.addAll(this.filters);
        return new CompositeEventFilter(this.logicType, combined);
    }

    /**
     * Creates a new composite filter by negating this one.
     */
    public EventFilter negate() {
        return (messageBody, headers) -> !this.accept(messageBody, headers);
    }
}