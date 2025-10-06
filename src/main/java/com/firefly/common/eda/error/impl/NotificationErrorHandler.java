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

package com.firefly.common.eda.error.impl;

import com.firefly.common.eda.error.CustomErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Example custom error handler that sends notifications for critical errors.
 * <p>
 * This handler demonstrates how to implement custom error handling logic
 * for specific error types or conditions.
 */
@Component
@ConditionalOnProperty(prefix = "firefly.eda.error.notification", name = "enabled", havingValue = "true")
@Slf4j
public class NotificationErrorHandler implements CustomErrorHandler {

    @Override
    public Mono<Void> handleError(Object event, Map<String, Object> headers, 
                                 Throwable error, String listenerMethod) {
        return Mono.fromRunnable(() -> {
            log.info("🚨 [Notification Handler] Critical error in {}: {}", 
                    listenerMethod, error.getMessage());
            
            // In a real implementation, this would:
            // - Send email/SMS notifications
            // - Create support tickets
            // - Send alerts to monitoring systems
            // - Log to external systems
            
            // Example notification logic
            if (isCriticalError(error)) {
                sendCriticalAlert(event, error, listenerMethod);
            } else {
                sendWarningAlert(event, error, listenerMethod);
            }
        });
    }

    @Override
    public String getHandlerName() {
        return "notification-error-handler";
    }

    @Override
    public boolean canHandle(Class<? extends Throwable> errorType) {
        // Handle specific error types that require notifications
        return RuntimeException.class.isAssignableFrom(errorType) ||
               IllegalStateException.class.isAssignableFrom(errorType) ||
               SecurityException.class.isAssignableFrom(errorType);
    }

    @Override
    public int getPriority() {
        return 100; // High priority for notifications
    }

    private boolean isCriticalError(Throwable error) {
        return error instanceof SecurityException ||
               error instanceof IllegalStateException ||
               error.getMessage().toLowerCase().contains("critical");
    }

    private void sendCriticalAlert(Object event, Throwable error, String listenerMethod) {
        log.error("🔥 CRITICAL ERROR ALERT: {} in {} - Event: {}", 
                 error.getMessage(), listenerMethod, event.getClass().getSimpleName());
        
        // Implementation would send actual notifications
        // notificationService.sendCriticalAlert(...)
    }

    private void sendWarningAlert(Object event, Throwable error, String listenerMethod) {
        log.warn("⚠️ WARNING ALERT: {} in {} - Event: {}", 
                error.getMessage(), listenerMethod, event.getClass().getSimpleName());
        
        // Implementation would send actual notifications
        // notificationService.sendWarningAlert(...)
    }
}
