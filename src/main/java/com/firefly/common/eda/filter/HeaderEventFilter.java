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
 * Event filter that matches based on message headers.
 * <p>
 * This filter checks for a specific header name and optionally matches
 * its value against a pattern that supports wildcards.
 */
@RequiredArgsConstructor
public class HeaderEventFilter implements EventFilter {

    private final String headerName;
    private final String expectedValue;
    private final Pattern compiledPattern;

    public HeaderEventFilter(String headerName, String expectedValue) {
        this.headerName = headerName;
        this.expectedValue = expectedValue;
        this.compiledPattern = compilePattern(expectedValue);
    }

    /**
     * Creates a filter that only checks for header presence (value can be anything).
     */
    public HeaderEventFilter(String headerName) {
        this.headerName = headerName;
        this.expectedValue = null;
        this.compiledPattern = null;
    }

    @Override
    public boolean accept(String messageBody, Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }

        Object headerValue = headers.get(headerName);
        
        // If we only care about presence, check if header exists
        if (expectedValue == null) {
            return headerValue != null;
        }
        
        // Check if header value matches expected value/pattern
        if (headerValue != null) {
            String actualValue = headerValue.toString();
            return matches(actualValue);
        }
        
        return false;
    }

    @Override
    public String getDescription() {
        if (expectedValue == null) {
            return "HeaderFilter[name=" + headerName + ", presence=required]";
        } else {
            return "HeaderFilter[name=" + headerName + ", value=" + expectedValue + "]";
        }
    }

    /**
     * Checks if the actual header value matches the expected pattern.
     */
    private boolean matches(String actualValue) {
        if (actualValue == null) {
            return expectedValue == null;
        }
        
        if (expectedValue == null) {
            return true; // Any non-null value is acceptable
        }
        
        // Exact match
        if (expectedValue.equals(actualValue)) {
            return true;
        }
        
        // Pattern match (if contains wildcards)
        if (expectedValue.contains("*") || expectedValue.contains("?")) {
            return compiledPattern.matcher(actualValue).matches();
        }
        
        return false;
    }

    /**
     * Compiles a glob pattern into a regex pattern.
     */
    private Pattern compilePattern(String globPattern) {
        if (globPattern == null || (!globPattern.contains("*") && !globPattern.contains("?"))) {
            // No wildcards, create exact match pattern
            return globPattern != null ? Pattern.compile(Pattern.quote(globPattern)) : null;
        }
        
        // Convert glob pattern to regex
        String regex = globPattern
                .replace(".", "\\.")     // Escape dots
                .replace("*", ".*")      // * becomes .*
                .replace("?", ".");      // ? becomes .
        
        return Pattern.compile(regex);
    }
}