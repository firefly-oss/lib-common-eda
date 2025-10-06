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
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Health information for an event publisher.
 */
@Data
public class PublisherHealth {
    private final PublisherType publisherType;
    private final boolean available;
    private final String status;
    private final String connectionId;
    private final Instant lastChecked;
    private final Map<String, Object> details;
    private final String errorMessage;
    
    public static PublisherHealthBuilder builder() {
        return new PublisherHealthBuilder();
    }
    
    public static class PublisherHealthBuilder {
        private PublisherType publisherType;
        private boolean available;
        private String status;
        private String connectionId;
        private Instant lastChecked = Instant.now();
        private Map<String, Object> details;
        private String errorMessage;
        
        public PublisherHealthBuilder publisherType(PublisherType publisherType) {
            this.publisherType = publisherType;
            return this;
        }
        
        public PublisherHealthBuilder available(boolean available) {
            this.available = available;
            return this;
        }
        
        public PublisherHealthBuilder status(String status) {
            this.status = status;
            return this;
        }
        
        public PublisherHealthBuilder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }
        
        public PublisherHealthBuilder lastChecked(Instant lastChecked) {
            this.lastChecked = lastChecked;
            return this;
        }
        
        public PublisherHealthBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public PublisherHealthBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public PublisherHealth build() {
            return new PublisherHealth(publisherType, available, status, connectionId, lastChecked, details, errorMessage);
        }
    }
}
