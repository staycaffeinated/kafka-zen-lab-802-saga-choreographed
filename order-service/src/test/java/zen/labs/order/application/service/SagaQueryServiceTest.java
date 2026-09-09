/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.outbound.SagaStateDataStore;
import zen.labs.order.domain.SagaState;
import zen.labs.order.domain.SagaStatus;
import zen.labs.order.domain.SagaStep;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaQueryService")
class SagaQueryServiceTest {

    @Mock
    private SagaStateDataStore sagaStateDataStore;

    @InjectMocks
    private SagaQueryService sagaQueryService;

    @Nested
    @DisplayName("findSaga")
    class FindSaga {

        @Test
        @DisplayName("should return SagaState when it exists for the given sagaId")
        void shouldReturnSagaState_whenSagaIdExists() {
            // Given
            String sagaId = "saga-abc-123";
            SagaState expected = SagaState.builder()
                    .sagaId(sagaId)
                    .orderId("order-001")
                    .status(SagaStatus.COMPLETED)
                    .currentStep(SagaStep.DONE)
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .updatedAt(Instant.parse("2026-01-01T00:01:00Z"))
                    .build();

            when(sagaStateDataStore.findBySagaId(sagaId)).thenReturn(Optional.of(expected));

            // When
            SagaState result = sagaQueryService.findSaga(sagaId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSagaId()).isEqualTo(sagaId);
            assertThat(result.getOrderId()).isEqualTo("order-001");
            assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(result.getCurrentStep()).isEqualTo(SagaStep.DONE);
            verify(sagaStateDataStore).findBySagaId(sagaId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no saga exists for the given sagaId")
        void shouldThrowResourceNotFoundException_whenSagaIdDoesNotExist() {
            // Given
            String sagaId = "saga-nonexistent-999";
            when(sagaStateDataStore.findBySagaId(sagaId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> sagaQueryService.findSaga(sagaId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(sagaId);

            verify(sagaStateDataStore).findBySagaId(sagaId);
        }
    }
}
