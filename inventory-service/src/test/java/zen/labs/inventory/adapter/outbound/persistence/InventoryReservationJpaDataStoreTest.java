/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import zen.labs.inventory.adapter.outbound.persistence.entity.InventoryReservationJpaEntity;
import zen.labs.inventory.adapter.outbound.persistence.repository.InventoryReservationRepository;
import zen.labs.inventory.domain.InventoryReservation;
import zen.labs.inventory.domain.ReservationStatus;

@ExtendWith(MockitoExtension.class)
class InventoryReservationJpaDataStoreTest {

    @Mock
    private InventoryReservationRepository repository;

    @InjectMocks
    private InventoryReservationJpaDataStore dataStore;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should pass entity with correct fields to repository and return mapped domain object")
        void shouldPassCorrectEntityToRepositoryAndReturnMappedDomain() {
            // Given
            Instant reservedAt = Instant.parse("2026-01-01T10:00:00Z");
            InventoryReservation domain = InventoryReservation.builder()
                    .sagaId("saga-001")
                    .orderId("order-001")
                    .status(ReservationStatus.RESERVED)
                    .reservedAt(reservedAt)
                    .build();

            InventoryReservationJpaEntity savedEntity = new InventoryReservationJpaEntity();
            savedEntity.setSagaId("saga-001");
            savedEntity.setOrderId("order-001");
            savedEntity.setStatus(ReservationStatus.RESERVED);
            savedEntity.setReservedAt(reservedAt);

            when(repository.save(any(InventoryReservationJpaEntity.class))).thenReturn(savedEntity);

            // When
            InventoryReservation result = dataStore.save(domain);

            // Then
            ArgumentCaptor<InventoryReservationJpaEntity> entityCaptor =
                    ArgumentCaptor.forClass(InventoryReservationJpaEntity.class);
            verify(repository).save(entityCaptor.capture());

            InventoryReservationJpaEntity captured = entityCaptor.getValue();
            assertThat(captured.getSagaId()).isEqualTo("saga-001");
            assertThat(captured.getOrderId()).isEqualTo("order-001");
            assertThat(captured.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(captured.getReservedAt()).isEqualTo(reservedAt);

            assertThat(result.getSagaId()).isEqualTo("saga-001");
            assertThat(result.getOrderId()).isEqualTo("order-001");
            assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(result.getReservedAt()).isEqualTo(reservedAt);
        }
    }

    @Nested
    @DisplayName("findBySagaId")
    class FindBySagaId {

        @Test
        @DisplayName("should return Optional with correct domain fields when entity found")
        void shouldReturnMappedDomain_whenEntityFound() {
            // Given
            Instant reservedAt = Instant.parse("2026-01-02T08:30:00Z");
            InventoryReservationJpaEntity entity = new InventoryReservationJpaEntity();
            entity.setSagaId("saga-002");
            entity.setOrderId("order-002");
            entity.setStatus(ReservationStatus.RESERVED);
            entity.setReservedAt(reservedAt);

            when(repository.findBySagaId("saga-002")).thenReturn(Optional.of(entity));

            // When
            Optional<InventoryReservation> result = dataStore.findBySagaId("saga-002");

            // Then
            assertThat(result).isPresent();
            InventoryReservation domain = result.get();
            assertThat(domain.getSagaId()).isEqualTo("saga-002");
            assertThat(domain.getOrderId()).isEqualTo("order-002");
            assertThat(domain.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(domain.getReservedAt()).isEqualTo(reservedAt);
        }

        @Test
        @DisplayName("should return Optional.empty() when entity not found")
        void shouldReturnEmpty_whenEntityNotFound() {
            // Given
            when(repository.findBySagaId("saga-999")).thenReturn(Optional.empty());

            // When
            Optional<InventoryReservation> result = dataStore.findBySagaId("saga-999");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should mutate entity status and save, returning domain with updated status when found")
        void shouldMutateStatusAndSave_whenEntityFound() {
            // Given
            Instant reservedAt = Instant.parse("2026-01-03T12:00:00Z");
            InventoryReservationJpaEntity existingEntity = new InventoryReservationJpaEntity();
            existingEntity.setSagaId("saga-003");
            existingEntity.setOrderId("order-003");
            existingEntity.setStatus(ReservationStatus.RESERVED);
            existingEntity.setReservedAt(reservedAt);

            InventoryReservation domainWithNewStatus = InventoryReservation.builder()
                    .sagaId("saga-003")
                    .orderId("order-003")
                    .status(ReservationStatus.RELEASED)
                    .reservedAt(reservedAt)
                    .build();

            when(repository.findBySagaId("saga-003")).thenReturn(Optional.of(existingEntity));
            when(repository.save(existingEntity)).thenAnswer(inv -> inv.getArgument(0));

            // When
            Optional<InventoryReservation> result = dataStore.update(domainWithNewStatus);

            // Then — entity status was mutated before save
            assertThat(existingEntity.getStatus()).isEqualTo(ReservationStatus.RELEASED);
            verify(repository).save(existingEntity);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(ReservationStatus.RELEASED);
            assertThat(result.get().getSagaId()).isEqualTo("saga-003");
        }

        @Test
        @DisplayName("should never call repository save and return Optional.empty() when entity not found")
        void shouldNeverSaveAndReturnEmpty_whenEntityNotFound() {
            // Given
            InventoryReservation domainWithNewStatus = InventoryReservation.builder()
                    .sagaId("saga-404")
                    .orderId("order-404")
                    .status(ReservationStatus.RELEASED)
                    .reservedAt(Instant.parse("2026-01-04T00:00:00Z"))
                    .build();

            when(repository.findBySagaId("saga-404")).thenReturn(Optional.empty());

            // When
            Optional<InventoryReservation> result = dataStore.update(domainWithNewStatus);

            // Then
            verify(repository, never()).save(any());
            assertThat(result).isEmpty();
        }
    }
}
