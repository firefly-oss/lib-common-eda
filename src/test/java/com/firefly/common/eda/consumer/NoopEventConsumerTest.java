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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NoopEventConsumer.
 */
@DisplayName("NoopEventConsumer Tests")
class NoopEventConsumerTest {

    private NoopEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NoopEventConsumer();
    }

    @Test
    @DisplayName("Should return empty flux for consume()")
    void shouldReturnEmptyFluxForConsume() {
        StepVerifier.create(consumer.consume())
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should return empty flux for consume() with destinations")
    void shouldReturnEmptyFluxForConsumeWithDestinations() {
        StepVerifier.create(consumer.consume("topic1", "topic2"))
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should start and stop successfully")
    void shouldStartAndStopSuccessfully() {
        // Initially not running
        assertThat(consumer.isRunning()).isFalse();

        // Start
        StepVerifier.create(consumer.start())
                .verifyComplete();
        assertThat(consumer.isRunning()).isTrue();

        // Stop
        StepVerifier.create(consumer.stop())
                .verifyComplete();
        assertThat(consumer.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should always be available")
    void shouldAlwaysBeAvailable() {
        assertThat(consumer.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should return correct consumer type")
    void shouldReturnCorrectConsumerType() {
        assertThat(consumer.getConsumerType()).isEqualTo("NOOP");
    }

    @Test
    @DisplayName("Should return health information")
    void shouldReturnHealthInformation() {
        StepVerifier.create(consumer.getHealth())
                .assertNext(health -> {
                    assertThat(health.getConsumerType()).isEqualTo("NOOP");
                    assertThat(health.isAvailable()).isTrue();
                    assertThat(health.getStatus()).isEqualTo("UP");
                    assertThat(health.getDetails()).containsKey("description");
                    assertThat(health.getDetails()).containsKey("suitable_for");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle multiple start/stop cycles")
    void shouldHandleMultipleStartStopCycles() {
        // Start
        StepVerifier.create(consumer.start())
                .verifyComplete();
        assertThat(consumer.isRunning()).isTrue();

        // Start again (should be idempotent)
        StepVerifier.create(consumer.start())
                .verifyComplete();
        assertThat(consumer.isRunning()).isTrue();

        // Stop
        StepVerifier.create(consumer.stop())
                .verifyComplete();
        assertThat(consumer.isRunning()).isFalse();

        // Stop again (should be idempotent)
        StepVerifier.create(consumer.stop())
                .verifyComplete();
        assertThat(consumer.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should handle null destinations gracefully")
    void shouldHandleNullDestinationsGracefully() {
        StepVerifier.create(consumer.consume((String[]) null))
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should handle empty destinations gracefully")
    void shouldHandleEmptyDestinationsGracefully() {
        StepVerifier.create(consumer.consume(new String[0]))
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }
}
