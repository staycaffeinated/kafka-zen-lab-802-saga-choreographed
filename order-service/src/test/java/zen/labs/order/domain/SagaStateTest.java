/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SagaState")
class SagaStateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private SagaState startedSaga() {
        return SagaState.builder()
                .sagaId("saga-001")
                .orderId("order-001")
                .status(SagaStatus.STARTED)
                .currentStep(SagaStep.RESERVE_INVENTORY)
                .startedAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Test
    @DisplayName("valid transition returns new state with updated fields")
    void validTransitionReturnsNewState() {
        Instant later = NOW.plusSeconds(10);

        SagaState next = startedSaga().transitionTo(SagaStatus.INVENTORY_RESERVED, SagaStep.PROCESS_PAYMENT, later);

        assertThat(next.getStatus()).isEqualTo(SagaStatus.INVENTORY_RESERVED);
        assertThat(next.getCurrentStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
        assertThat(next.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("valid transition preserves sagaId, orderId, startedAt")
    void validTransitionPreservesIdentityFields() {
        SagaState next = startedSaga().transitionTo(SagaStatus.INVENTORY_RESERVED, SagaStep.PROCESS_PAYMENT, NOW);

        assertThat(next.getSagaId()).isEqualTo("saga-001");
        assertThat(next.getOrderId()).isEqualTo("order-001");
        assertThat(next.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("invalid transition throws SagaDomainException")
    void invalidTransitionThrows() {
        var startOfSaga = startedSaga();
        assertThatThrownBy(() -> startOfSaga.transitionTo(SagaStatus.COMPLETED, SagaStep.DONE, NOW))
                .isInstanceOf(SagaDomainException.class)
                .hasMessageContaining("STARTED")
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("saga-001");
    }

    @Test
    @DisplayName("terminal state cannot transition")
    void terminalStateCannotTransition() {
        SagaState completed = startedSaga()
                .transitionTo(SagaStatus.INVENTORY_RESERVED, SagaStep.PROCESS_PAYMENT, NOW)
                .transitionTo(SagaStatus.PAYMENT_PROCESSED, SagaStep.CREATE_SHIPMENT, NOW)
                .transitionTo(SagaStatus.COMPLETED, SagaStep.DONE, NOW);

        assertThatThrownBy(() -> completed.transitionTo(SagaStatus.FAILED, SagaStep.DONE, NOW))
                .isInstanceOf(SagaDomainException.class);
    }
}
