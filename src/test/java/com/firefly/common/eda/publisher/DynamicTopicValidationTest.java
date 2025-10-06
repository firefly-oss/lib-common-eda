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
import com.firefly.common.eda.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for validation and error handling improvements in dynamic topic selection.
 * <p>
 * This test class verifies the enhanced validation and error handling features
 * added to the dynamic topic selection functionality.
 */
@DisplayName("Dynamic Topic Selection Validation")
class DynamicTopicValidationTest extends BaseIntegrationTest {

    @Autowired
    private EventPublisherFactory publisherFactory;

    @Test
    @DisplayName("Should handle null publisher type gracefully")
    void shouldHandleNullPublisherTypeGracefully() {
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                null, "test-destination");
        
        // Assert
        assertThat(publisher).isNull();
    }

    @Test
    @DisplayName("Should handle null publisher type with connection ID gracefully")
    void shouldHandleNullPublisherTypeWithConnectionIdGracefully() {
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                null, "test-connection", "test-destination");
        
        // Assert
        assertThat(publisher).isNull();
    }

    @Test
    @DisplayName("Should trim whitespace from custom destinations")
    void shouldTrimWhitespaceFromCustomDestinations() {
        // Arrange
        String destinationWithWhitespace = "  test-destination  ";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, destinationWithWhitespace);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo("test-destination");
    }

    @Test
    @DisplayName("Should handle very long destination names with warning")
    void shouldHandleVeryLongDestinationNamesWithWarning() {
        // Arrange
        String longDestination = "a".repeat(1500); // Longer than 1000 characters
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, longDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(longDestination);
    }

    @Test
    @DisplayName("Should handle destination with mixed whitespace correctly")
    void shouldHandleDestinationWithMixedWhitespaceCorrectly() {
        // Arrange
        String destinationWithMixedWhitespace = "\t\n  test-destination-with-spaces  \r\n\t";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, destinationWithMixedWhitespace);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo("test-destination-with-spaces");
    }

    @Test
    @DisplayName("Should handle empty string after trimming")
    void shouldHandleEmptyStringAfterTrimming() {
        // Arrange
        String emptyAfterTrim = "   \t\n\r   ";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, emptyAfterTrim);
        
        // Assert
        assertThat(publisher).isNotNull();
        // Should fall back to default destination since trimmed string is empty
        assertThat(publisher.getDefaultDestination()).isEqualTo("application-events");
    }

    @Test
    @DisplayName("Should handle destination with only special characters")
    void shouldHandleDestinationWithOnlySpecialCharacters() {
        // Arrange
        String specialCharsDestination = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, specialCharsDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(specialCharsDestination);
    }

    @Test
    @DisplayName("Should handle destination with newlines and tabs")
    void shouldHandleDestinationWithNewlinesAndTabs() {
        // Arrange
        String destinationWithNewlines = "test\ndestination\twith\rspecial\nchars";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, destinationWithNewlines);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(destinationWithNewlines);
    }

    @Test
    @DisplayName("Should handle destination with leading and trailing special characters")
    void shouldHandleDestinationWithLeadingAndTrailingSpecialCharacters() {
        // Arrange
        String destinationWithSpecialChars = "   !!!test-destination!!!   ";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, destinationWithSpecialChars);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo("!!!test-destination!!!");
    }

    @Test
    @DisplayName("Should handle destination with numeric characters only")
    void shouldHandleDestinationWithNumericCharactersOnly() {
        // Arrange
        String numericDestination = "1234567890";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, numericDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(numericDestination);
    }

    @Test
    @DisplayName("Should handle destination with single character")
    void shouldHandleDestinationWithSingleCharacter() {
        // Arrange
        String singleCharDestination = "a";
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, singleCharDestination);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(singleCharDestination);
    }

    @Test
    @DisplayName("Should handle destination at exactly 1000 characters")
    void shouldHandleDestinationAtExactly1000Characters() {
        // Arrange
        String exactlyThousandChars = "a".repeat(1000);
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, exactlyThousandChars);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(exactlyThousandChars);
        assertThat(publisher.getDefaultDestination().length()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should handle destination at 1001 characters (triggers warning)")
    void shouldHandleDestinationAt1001Characters() {
        // Arrange
        String overThousandChars = "a".repeat(1001);
        
        // Act
        EventPublisher publisher = publisherFactory.getPublisherWithDestination(
                PublisherType.APPLICATION_EVENT, overThousandChars);
        
        // Assert
        assertThat(publisher).isNotNull();
        assertThat(publisher.getDefaultDestination()).isEqualTo(overThousandChars);
        assertThat(publisher.getDefaultDestination().length()).isEqualTo(1001);
    }
}
