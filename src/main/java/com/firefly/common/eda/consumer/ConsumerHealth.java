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

package com.firefly.common.eda.consumer;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Health information for an event consumer.
 */
@Data
public class ConsumerHealth {
    private final String consumerType;
    private final boolean available;
    private final boolean running;
    private final String status;
    private final String connectionId;
    private final Instant lastChecked;
    private final Map<String, Object> details;
    private final String errorMessage;
    private final Long messagesConsumed;
    private final Long messagesProcessed;
    private final Long messagesFailures;
    
    public static ConsumerHealthBuilder builder() {
        return new ConsumerHealthBuilder();
    }
    
    public static class ConsumerHealthBuilder {
        private String consumerType;
        private boolean available;
        private boolean running;
        private String status;
        private String connectionId;
        private Instant lastChecked = Instant.now();
        private Map<String, Object> details;
        private String errorMessage;
        private Long messagesConsumed;
        private Long messagesProcessed;
        private Long messagesFailures;
        
        public ConsumerHealthBuilder consumerType(String consumerType) {
            this.consumerType = consumerType;
            return this;
        }
        
        public ConsumerHealthBuilder available(boolean available) {
            this.available = available;
            return this;
        }
        
        public ConsumerHealthBuilder running(boolean running) {
            this.running = running;
            return this;
        }
        
        public ConsumerHealthBuilder status(String status) {
            this.status = status;
            return this;
        }
        
        public ConsumerHealthBuilder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }
        
        public ConsumerHealthBuilder lastChecked(Instant lastChecked) {
            this.lastChecked = lastChecked;
            return this;
        }
        
        public ConsumerHealthBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public ConsumerHealthBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public ConsumerHealthBuilder messagesConsumed(Long messagesConsumed) {
            this.messagesConsumed = messagesConsumed;
            return this;
        }
        
        public ConsumerHealthBuilder messagesProcessed(Long messagesProcessed) {
            this.messagesProcessed = messagesProcessed;
            return this;
        }
        
        public ConsumerHealthBuilder messagesFailures(Long messagesFailures) {
            this.messagesFailures = messagesFailures;
            return this;
        }
        
        public ConsumerHealth build() {
            return new ConsumerHealth(consumerType, available, running, status, connectionId, lastChecked, details, errorMessage, messagesConsumed, messagesProcessed, messagesFailures);
        }
    }
}
