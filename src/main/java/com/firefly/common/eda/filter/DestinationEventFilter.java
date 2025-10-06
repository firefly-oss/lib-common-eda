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

import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Event filter that matches based on message destination.
 * <p>
 * This filter checks various header keys that might contain destination
 * information (topic, queue, destination, etc.) and matches them against
 * a configured pattern that supports wildcards.
 */
@RequiredArgsConstructor
public class DestinationEventFilter implements EventFilter {

    private final String destinationPattern;
    private final Pattern compiledPattern;

    public DestinationEventFilter(String destinationPattern) {
        this.destinationPattern = destinationPattern;
        this.compiledPattern = compilePattern(destinationPattern);
    }

    @Override
    public boolean accept(String messageBody, Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }

        // Check various header keys that might contain destination info
        String[] destinationKeys = {"destination", "topic", "queue", "exchange", "subject", "channel"};
        
        for (String key : destinationKeys) {
            Object value = headers.get(key);
            if (value != null) {
                String destination = value.toString();
                if (matches(destination)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getDescription() {
        return "DestinationFilter[pattern=" + destinationPattern + "]";
    }

    /**
     * Checks if the destination matches the configured pattern.
     */
    private boolean matches(String destination) {
        if (destination == null) {
            return false;
        }
        
        // Exact match
        if (destinationPattern.equals(destination)) {
            return true;
        }
        
        // Pattern match (if contains wildcards)
        if (destinationPattern.contains("*") || destinationPattern.contains("?")) {
            return compiledPattern.matcher(destination).matches();
        }
        
        return false;
    }

    /**
     * Compiles a glob pattern into a regex pattern.
     */
    private Pattern compilePattern(String globPattern) {
        if (!globPattern.contains("*") && !globPattern.contains("?")) {
            // No wildcards, create exact match pattern
            return Pattern.compile(Pattern.quote(globPattern));
        }
        
        // Convert glob pattern to regex
        String regex = globPattern
                .replace(".", "\\.")     // Escape dots
                .replace("*", ".*")      // * becomes .*
                .replace("?", ".");      // ? becomes .
        
        return Pattern.compile(regex);
    }
}