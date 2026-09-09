/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.order.adapter.outbound.persistence.entity.SagaStateJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.SagaStateRepository;
import zen.labs.order.domain.SagaState;
import zen.labs.order.domain.SagaStatus;
import zen.labs.order.domain.SagaStep;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaStateJpaDataStore")
class SagaStateJpaDataStoreTest {

    @Mock
    private SagaStateRepository sagaStateRepository;

    @InjectMocks
    private SagaStateJpaDataStore sagaStateJpaDataStore;

    private static final String SAGA_ID = "saga-abc-123";
    private static final String ORDER_ID = "order-xyz-456";
    private static final SagaStatus INITIAL_STATUS = SagaStatus.STARTED;
    private static final SagaStep INITIAL_STEP = SagaStep.RESERVE_INVENTORY;
    private static final Instant STARTED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-15T10:05:00Z");

    private SagaStateJpaEntity buildSagaStateEntity() {
        SagaStateJpaEntity entity = new SagaStateJpaEntity();
        entity.setSagaId(SAGA_ID);
        entity.setOrderId(ORDER_ID);
        entity.setStatus(INITIAL_STATUS);
        entity.setCurrentStep(INITIAL_STEP);
        entity.setStartedAt(STARTED_AT);
        entity.setUpdatedAt(UPDATED_AT);
        return entity;
    }

    private SagaState buildSagaStateDomain() {
        return SagaState.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_ID)
                .status(INITIAL_STATUS)
                .currentStep(INITIAL_STEP)
                .startedAt(STARTED_AT)
                .updatedAt(UPDATED_AT)
                .build();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should persist entity with all fields mapped correctly and return mapped domain")
        void shouldPersistEntityWithAllFields_andReturnMappedDomain() {
            // Given
            SagaState domain = buildSagaStateDomain();
            SagaStateJpaEntity savedEntity = buildSagaStateEntity();

            ArgumentCaptor<SagaStateJpaEntity> entityCaptor = ArgumentCaptor.forClass(SagaStateJpaEntity.class);
            when(sagaStateRepository.save(entityCaptor.capture())).thenReturn(savedEntity);

            // When
            SagaState result = sagaStateJpaDataStore.save(domain);

            // Then
            SagaStateJpaEntity captured = entityCaptor.getValue();
            assertThat(captured.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(captured.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(captured.getStatus()).isEqualTo(INITIAL_STATUS);
            assertThat(captured.getCurrentStep()).isEqualTo(INITIAL_STEP);
            assertThat(captured.getStartedAt()).isEqualTo(STARTED_AT);
            assertThat(captured.getUpdatedAt()).isEqualTo(UPDATED_AT);

            assertThat(result.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getStatus()).isEqualTo(INITIAL_STATUS);
            assertThat(result.getCurrentStep()).isEqualTo(INITIAL_STEP);
            assertThat(result.getStartedAt()).isEqualTo(STARTED_AT);
            assertThat(result.getUpdatedAt()).isEqualTo(UPDATED_AT);

            verify(sagaStateRepository).save(any(SagaStateJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("findBySagaId")
    class FindBySagaId {

        @Test
        @DisplayName("should return mapped domain SagaState with all fields when entity is found")
        void shouldReturnMappedDomain_whenEntityFound() {
            // Given
            SagaStateJpaEntity entity = buildSagaStateEntity();
            when(sagaStateRepository.findBySagaId(SAGA_ID)).thenReturn(Optional.of(entity));

            // When
            Optional<SagaState> result = sagaStateJpaDataStore.findBySagaId(SAGA_ID);

            // Then
            assertThat(result).isPresent();
            SagaState sagaState = result.get();
            assertThat(sagaState.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(sagaState.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(sagaState.getStatus()).isEqualTo(INITIAL_STATUS);
            assertThat(sagaState.getCurrentStep()).isEqualTo(INITIAL_STEP);
            assertThat(sagaState.getStartedAt()).isEqualTo(STARTED_AT);
            assertThat(sagaState.getUpdatedAt()).isEqualTo(UPDATED_AT);

            verify(sagaStateRepository).findBySagaId(SAGA_ID);
        }

        @Test
        @DisplayName("should return empty Optional when entity is not found")
        void shouldReturnEmptyOptional_whenEntityNotFound() {
            // Given
            when(sagaStateRepository.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When
            Optional<SagaState> result = sagaStateJpaDataStore.findBySagaId(SAGA_ID);

            // Then
            assertThat(result).isEmpty();
            verify(sagaStateRepository).findBySagaId(SAGA_ID);
        }
    }

    @Nested
    @DisplayName("findByOrderId")
    class FindByOrderId {

        @Test
        @DisplayName("should return mapped domain SagaState when entity is found by orderId")
        void shouldReturnMappedDomain_whenEntityFound() {
            // Given
            SagaStateJpaEntity entity = buildSagaStateEntity();
            when(sagaStateRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(entity));

            // When
            Optional<SagaState> result = sagaStateJpaDataStore.findByOrderId(ORDER_ID);

            // Then
            assertThat(result).isPresent();
            SagaState sagaState = result.get();
            assertThat(sagaState.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(sagaState.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(sagaState.getStatus()).isEqualTo(INITIAL_STATUS);
            verify(sagaStateRepository).findByOrderId(ORDER_ID);
        }

        @Test
        @DisplayName("should return empty Optional when no saga exists for the orderId")
        void shouldReturnEmptyOptional_whenEntityNotFound() {
            // Given
            when(sagaStateRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            // When
            Optional<SagaState> result = sagaStateJpaDataStore.findByOrderId(ORDER_ID);

            // Then
            assertThat(result).isEmpty();
            verify(sagaStateRepository).findByOrderId(ORDER_ID);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update status, currentStep, and updatedAt on entity, save, and return mapped domain")
        void shouldUpdateStatusCurrentStepAndUpdatedAt_andReturnMappedDomain_whenEntityFound() {
            // Given
            SagaStateJpaEntity existingEntity = buildSagaStateEntity();

            Instant newUpdatedAt = Instant.parse("2026-01-15T10:30:00Z");
            SagaState updateDomain = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_ID)
                    .status(SagaStatus.INVENTORY_RESERVED)
                    .currentStep(SagaStep.PROCESS_PAYMENT)
                    .startedAt(STARTED_AT)
                    .updatedAt(newUpdatedAt)
                    .build();

            SagaStateJpaEntity savedEntity = new SagaStateJpaEntity();
            savedEntity.setSagaId(SAGA_ID);
            savedEntity.setOrderId(ORDER_ID);
            savedEntity.setStatus(SagaStatus.INVENTORY_RESERVED);
            savedEntity.setCurrentStep(SagaStep.PROCESS_PAYMENT);
            savedEntity.setStartedAt(STARTED_AT);
            savedEntity.setUpdatedAt(newUpdatedAt);

            when(sagaStateRepository.findBySagaId(SAGA_ID)).thenReturn(Optional.of(existingEntity));
            when(sagaStateRepository.save(existingEntity)).thenReturn(savedEntity);

            // When
            Optional<SagaState> result = sagaStateJpaDataStore.update(updateDomain);

            // Then
            assertThat(existingEntity.getStatus()).isEqualTo(SagaStatus.INVENTORY_RESERVED);
            assertThat(existingEntity.getCurrentStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
            assertThat(existingEntity.getUpdatedAt()).isEqualTo(newUpdatedAt);

            assertThat(result).isPresent();
            SagaState updatedState = result.get();
            assertThat(updatedState.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(updatedState.getStatus()).isEqualTo(SagaStatus.INVENTORY_RESERVED);
            assertThat(updatedState.getCurrentStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
            assertThat(updatedState.getUpdatedAt()).isEqualTo(newUpdatedAt);

            verify(sagaStateRepository).findBySagaId(SAGA_ID);
            verify(sagaStateRepository).save(existingEntity);
        }

        @Test
        @DisplayName("should return empty Optional when entity is not found")
        void shouldReturnEmptyOptional_whenEntityNotFound() {
            // Given
            SagaState updateDomain = buildSagaStateDomain();
            when(sagaStateRepository.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When
            Optional<SagaState> result = sagaStateJpaDataStore.update(updateDomain);

            // Then
            assertThat(result).isEmpty();
            verify(sagaStateRepository).findBySagaId(SAGA_ID);
        }
    }
}
