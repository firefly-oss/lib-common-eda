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

package com.firefly.common.eda.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for evaluating Spring Expression Language (SpEL) expressions.
 * <p>
 * This component provides thread-safe evaluation of SpEL expressions with support for:
 * <ul>
 *   <li>Method parameters (#param0, #param1, etc.)</li>
 *   <li>Method result (#result)</li>
 *   <li>Custom variables</li>
 *   <li>Expression caching for performance</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * // Evaluate with method parameters
 * String destination = evaluator.evaluateExpression(
 *     "#{#param0.tenantId}-events",
 *     method,
 *     args,
 *     null,
 *     String.class
 * );
 * 
 * // Evaluate with result
 * boolean condition = evaluator.evaluateExpression(
 *     "#result != null && #result.isActive()",
 *     method,
 *     args,
 *     result,
 *     Boolean.class
 * );
 * }
 * </pre>
 */
@Component
@Slf4j
public class SpelExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Evaluates a SpEL expression with method context.
     *
     * @param expressionString the SpEL expression to evaluate
     * @param method the method being invoked
     * @param args the method arguments
     * @param result the method result (can be null if evaluating before method execution)
     * @param expectedType the expected return type
     * @param <T> the type of the result
     * @return the evaluated value, or null if expression is empty or evaluation fails
     */
    public <T> T evaluateExpression(String expressionString, Method method, Object[] args, 
                                    Object result, Class<T> expectedType) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            return null;
        }

        try {
            // Remove SpEL delimiters if present (#{...})
            String cleanExpression = cleanExpression(expressionString);
            
            // Get or parse expression
            Expression expression = expressionCache.computeIfAbsent(cleanExpression, parser::parseExpression);
            
            // Create evaluation context
            EvaluationContext context = createEvaluationContext(method, args, result);
            
            // Evaluate expression
            return expression.getValue(context, expectedType);
            
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression '{}': {}", expressionString, e.getMessage());
            return null;
        }
    }

    /**
     * Evaluates a SpEL expression and returns a String result.
     *
     * @param expressionString the SpEL expression to evaluate
     * @param method the method being invoked
     * @param args the method arguments
     * @param result the method result
     * @return the evaluated string, or the original expression if evaluation fails
     */
    public String evaluateAsString(String expressionString, Method method, Object[] args, Object result) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            return expressionString;
        }

        // If it doesn't look like a SpEL expression, return as-is
        if (!isSpelExpression(expressionString)) {
            return expressionString;
        }

        String evaluated = evaluateExpression(expressionString, method, args, result, String.class);
        return evaluated != null ? evaluated : expressionString;
    }

    /**
     * Evaluates a boolean condition expression.
     *
     * @param conditionExpression the condition expression
     * @param method the method being invoked
     * @param args the method arguments
     * @param result the method result
     * @return true if condition evaluates to true, false otherwise
     */
    public boolean evaluateCondition(String conditionExpression, Method method, Object[] args, Object result) {
        if (conditionExpression == null || conditionExpression.trim().isEmpty()) {
            return true; // Empty condition means always true
        }

        Boolean evaluated = evaluateExpression(conditionExpression, method, args, result, Boolean.class);
        return Boolean.TRUE.equals(evaluated);
    }

    /**
     * Evaluates header expressions and returns a map of header key-value pairs.
     *
     * @param headerExpressions array of header expressions in format "key=value"
     * @param method the method being invoked
     * @param args the method arguments
     * @param result the method result
     * @return map of evaluated headers
     */
    public Map<String, Object> evaluateHeaders(String[] headerExpressions, Method method, Object[] args, Object result) {
        Map<String, Object> headers = new HashMap<>();
        
        if (headerExpressions == null || headerExpressions.length == 0) {
            return headers;
        }

        for (String headerExpression : headerExpressions) {
            if (headerExpression == null || headerExpression.trim().isEmpty()) {
                continue;
            }

            // Parse "key=value" format
            int equalsIndex = headerExpression.indexOf('=');
            if (equalsIndex > 0) {
                String key = headerExpression.substring(0, equalsIndex).trim();
                String valueExpression = headerExpression.substring(equalsIndex + 1).trim();
                
                // Evaluate the value expression
                String value = evaluateAsString(valueExpression, method, args, result);
                headers.put(key, value);
            } else {
                log.warn("Invalid header expression format (expected 'key=value'): {}", headerExpression);
            }
        }

        return headers;
    }

    /**
     * Creates an evaluation context with method parameters and result.
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Add method parameters as variables
        if (args != null && args.length > 0) {
            // Add indexed parameters (param0, param1, etc.)
            for (int i = 0; i < args.length; i++) {
                context.setVariable("param" + i, args[i]);
            }
            
            // Try to add named parameters if available
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            if (parameterNames != null) {
                for (int i = 0; i < Math.min(parameterNames.length, args.length); i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
        }
        
        // Add result variable if present
        if (result != null) {
            context.setVariable("result", result);
        }
        
        return context;
    }

    /**
     * Removes SpEL delimiters from expression string.
     */
    private String cleanExpression(String expression) {
        if (expression == null) {
            return null;
        }
        
        String trimmed = expression.trim();
        
        // Remove #{...} delimiters
        if (trimmed.startsWith("#{") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1);
        }
        
        // Remove ${...} delimiters (property placeholders)
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1);
        }
        
        return trimmed;
    }

    /**
     * Checks if a string looks like a SpEL expression.
     */
    private boolean isSpelExpression(String expression) {
        if (expression == null) {
            return false;
        }
        
        String trimmed = expression.trim();
        return (trimmed.startsWith("#{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("#") && !trimmed.startsWith("${"));
    }

    /**
     * Clears the expression cache.
     * Useful for testing or when expressions need to be re-parsed.
     */
    public void clearCache() {
        expressionCache.clear();
    }

    /**
     * Gets the current cache size.
     */
    public int getCacheSize() {
        return expressionCache.size();
    }
}

