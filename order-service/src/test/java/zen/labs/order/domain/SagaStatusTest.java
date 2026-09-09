/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("SagaStatus")
class SagaStatusTest {

    @Nested
    @DisplayName("STARTED")
    class Started {

        @Test
        void canTransitionToInventoryReserved() {
            assertThat(SagaStatus.STARTED.canTransitionTo(SagaStatus.INVENTORY_RESERVED))
                    .isTrue();
        }

        @Test
        void canTransitionToFailed() {
            assertThat(SagaStatus.STARTED.canTransitionTo(SagaStatus.FAILED)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(
                value = SagaStatus.class,
                names = {"STARTED", "PAYMENT_PROCESSED", "COMPLETED", "COMPENSATING"})
        void cannotTransitionToOthers(SagaStatus next) {
            assertThat(SagaStatus.STARTED.canTransitionTo(next)).isFalse();
        }
    }

    @Nested
    @DisplayName("INVENTORY_RESERVED")
    class InventoryReserved {

        @Test
        void canTransitionToPaymentProcessed() {
            assertThat(SagaStatus.INVENTORY_RESERVED.canTransitionTo(SagaStatus.PAYMENT_PROCESSED))
                    .isTrue();
        }

        @Test
        void canTransitionToCompensating() {
            assertThat(SagaStatus.INVENTORY_RESERVED.canTransitionTo(SagaStatus.COMPENSATING))
                    .isTrue();
        }

        @ParameterizedTest
        @EnumSource(
                value = SagaStatus.class,
                names = {"STARTED", "INVENTORY_RESERVED", "COMPLETED", "FAILED"})
        void cannotTransitionToOthers(SagaStatus next) {
            assertThat(SagaStatus.INVENTORY_RESERVED.canTransitionTo(next)).isFalse();
        }
    }

    @Nested
    @DisplayName("PAYMENT_PROCESSED")
    class PaymentProcessed {

        @Test
        void canTransitionToCompleted() {
            assertThat(SagaStatus.PAYMENT_PROCESSED.canTransitionTo(SagaStatus.COMPLETED))
                    .isTrue();
        }

        @Test
        void canTransitionToCompensating() {
            assertThat(SagaStatus.PAYMENT_PROCESSED.canTransitionTo(SagaStatus.COMPENSATING))
                    .isTrue();
        }

        @ParameterizedTest
        @EnumSource(
                value = SagaStatus.class,
                names = {"STARTED", "INVENTORY_RESERVED", "PAYMENT_PROCESSED", "FAILED"})
        void cannotTransitionToOthers(SagaStatus next) {
            assertThat(SagaStatus.PAYMENT_PROCESSED.canTransitionTo(next)).isFalse();
        }
    }

    @Nested
    @DisplayName("COMPENSATING")
    class Compensating {

        @Test
        void canTransitionToCompensating() {
            assertThat(SagaStatus.COMPENSATING.canTransitionTo(SagaStatus.COMPENSATING))
                    .isTrue();
        }

        @Test
        void canTransitionToFailed() {
            assertThat(SagaStatus.COMPENSATING.canTransitionTo(SagaStatus.FAILED))
                    .isTrue();
        }

        @ParameterizedTest
        @EnumSource(
                value = SagaStatus.class,
                names = {"STARTED", "INVENTORY_RESERVED", "PAYMENT_PROCESSED", "COMPLETED"})
        void cannotTransitionToOthers(SagaStatus next) {
            assertThat(SagaStatus.COMPENSATING.canTransitionTo(next)).isFalse();
        }
    }

    @Nested
    @DisplayName("Terminal states")
    class Terminal {

        @ParameterizedTest
        @EnumSource(SagaStatus.class)
        void completedCannotTransitionToAnything(SagaStatus next) {
            assertThat(SagaStatus.COMPLETED.canTransitionTo(next)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(SagaStatus.class)
        void failedCannotTransitionToAnything(SagaStatus next) {
            assertThat(SagaStatus.FAILED.canTransitionTo(next)).isFalse();
        }
    }
}
