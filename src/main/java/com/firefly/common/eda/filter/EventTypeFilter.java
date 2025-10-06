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

import com.firefly.common.eda.serialization.MessageSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Event filter that matches based on event type/class.
 * <p>
 * This filter can match based on:
 * <ul>
 *   <li>Event class name from headers (event_class, event_type)</li>
 *   <li>Deserializing the message and checking the actual class</li>
 *   <li>Pattern matching with wildcards</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public class EventTypeFilter implements EventFilter {

    private final String eventTypePattern;
    private final Pattern compiledPattern;
    private final MessageSerializer messageSerializer;

    public EventTypeFilter(String eventTypePattern) {
        this.eventTypePattern = eventTypePattern;
        this.compiledPattern = compilePattern(eventTypePattern);
        this.messageSerializer = null; // Will use header-based matching only
    }

    public EventTypeFilter(String eventTypePattern, MessageSerializer messageSerializer) {
        this.eventTypePattern = eventTypePattern;
        this.compiledPattern = compilePattern(eventTypePattern);
        this.messageSerializer = messageSerializer;
    }

    @Override
    public boolean accept(String messageBody, Map<String, Object> headers) {
        // First try header-based matching (faster)
        if (matchesFromHeaders(headers)) {
            return true;
        }

        // If header matching fails and we have a serializer, try deserializing
        if (messageSerializer != null && messageBody != null) {
            return matchesFromDeserialization(messageBody);
        }

        return false;
    }

    @Override
    public String getDescription() {
        return "EventTypeFilter[pattern=" + eventTypePattern + "]";
    }

    /**
     * Attempts to match event type from message headers.
     */
    private boolean matchesFromHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }

        // Check various header keys that might contain event type info
        String[] eventTypeKeys = {"event_class", "event_type", "message_type", "type", "class"};
        
        for (String key : eventTypeKeys) {
            Object value = headers.get(key);
            if (value != null) {
                String eventType = value.toString();
                if (matches(eventType)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Attempts to match event type by deserializing the message.
     */
    private boolean matchesFromDeserialization(String messageBody) {
        try {
            Object event = messageSerializer.deserialize(messageBody, Object.class);
            if (event != null) {
                String eventClassName = event.getClass().getName();
                String simpleClassName = event.getClass().getSimpleName();
                
                return matches(eventClassName) || matches(simpleClassName);
            }
        } catch (Exception e) {
            log.debug("Failed to deserialize message for event type filtering: {}", e.getMessage());
        }
        
        return false;
    }

    /**
     * Checks if the event type matches the configured pattern.
     */
    private boolean matches(String eventType) {
        if (eventType == null) {
            return false;
        }
        
        // Exact match
        if (eventTypePattern.equals(eventType)) {
            return true;
        }
        
        // Pattern match (if contains wildcards)
        if (eventTypePattern.contains("*") || eventTypePattern.contains("?")) {
            return compiledPattern.matcher(eventType).matches();
        }
        
        // Partial match for class names (e.g., "UserEvent" matches "com.example.UserEvent")
        if (eventType.endsWith("." + eventTypePattern) || eventType.endsWith(eventTypePattern)) {
            return true;
        }
        
        return false;
    }

    /**
     * Compiles a glob pattern into a regex pattern.
     */
    private Pattern compilePattern(String globPattern) {
        if (!globPattern.contains("*") && !globPattern.contains("?")) {
            // No wildcards, create partial match pattern for class names
            String regex = ".*\\b" + Pattern.quote(globPattern) + "\\b.*";
            return Pattern.compile(regex);
        }
        
        // Convert glob pattern to regex
        String regex = globPattern
                .replace(".", "\\.")     // Escape dots
                .replace("*", ".*")      // * becomes .*
                .replace("?", ".");      // ? becomes .
        
        return Pattern.compile(regex);
    }
}