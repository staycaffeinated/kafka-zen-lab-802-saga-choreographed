package zen.labs.shipping.adapter.outbound.persistence;

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
import zen.labs.shipping.adapter.outbound.persistence.entity.ShipmentJpaEntity;
import zen.labs.shipping.adapter.outbound.persistence.repository.ShipmentRepository;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@ExtendWith(MockitoExtension.class)
class ShipmentJpaDataStoreTest {

    @Mock
    private ShipmentRepository repository;

    @InjectMocks
    private ShipmentJpaDataStore dataStore;

    /* ---------------------------------------------------------------------------------
     * HELPERS
     * --------------------------------------------------------------------------------- */

    private static ShipmentJpaEntity entityWith(
            String orderId, String address, ShipmentStatus status, Instant createdAt) {
        ShipmentJpaEntity e = new ShipmentJpaEntity();
        e.setOrderId(orderId);
        e.setShippingAddress(address);
        e.setStatus(status);
        e.setCreatedAt(createdAt);
        return e;
    }

    /* ---------------------------------------------------------------------------------
     * save
     * --------------------------------------------------------------------------------- */

    @Nested
    @DisplayName("save")
    class Save {

        private static final String ORDER_ID = "order-001";
        private static final String ADDRESS = "123 Main St, Springfield, IL 62701";
        private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");

        @Test
        @DisplayName("should pass entity with all domain fields to the repository")
        void shouldPersistEntityWithAllFields() {
            Shipment domain = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress(ADDRESS)
                    .status(ShipmentStatus.CREATED)
                    .createdAt(CREATED_AT)
                    .build();

            ShipmentJpaEntity savedEntity = entityWith(ORDER_ID, ADDRESS, ShipmentStatus.CREATED, CREATED_AT);
            when(repository.save(any(ShipmentJpaEntity.class))).thenReturn(savedEntity);

            dataStore.save(domain);

            ArgumentCaptor<ShipmentJpaEntity> captor = ArgumentCaptor.forClass(ShipmentJpaEntity.class);
            verify(repository).save(captor.capture());

            ShipmentJpaEntity captured = captor.getValue();
            assertThat(captured.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(captured.getShippingAddress()).isEqualTo(ADDRESS);
            assertThat(captured.getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(captured.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("should map the persisted entity back to a domain object with all fields")
        void shouldReturnDomainMappedFromSavedEntity() {
            Shipment domain = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress(ADDRESS)
                    .status(ShipmentStatus.CREATED)
                    .createdAt(CREATED_AT)
                    .build();

            ShipmentJpaEntity savedEntity = entityWith(ORDER_ID, ADDRESS, ShipmentStatus.CREATED, CREATED_AT);
            when(repository.save(any(ShipmentJpaEntity.class))).thenReturn(savedEntity);

            Shipment result = dataStore.save(domain);

            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getShippingAddress()).isEqualTo(ADDRESS);
            assertThat(result.getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
        }
    }

    /* ---------------------------------------------------------------------------------
     * findByOrderId
     * --------------------------------------------------------------------------------- */

    @Nested
    @DisplayName("findByOrderId")
    class FindByOrderId {

        private static final String ORDER_ID = "order-002";
        private static final Instant CREATED_AT = Instant.parse("2026-09-01T11:00:00Z");

        @Test
        @DisplayName("should return a populated Optional with correct domain fields when entity is found")
        void shouldReturnDomainWhenEntityFound() {
            ShipmentJpaEntity entity =
                    entityWith(ORDER_ID, "456 Elm Ave, Portland, OR 97201", ShipmentStatus.CREATED, CREATED_AT);
            when(repository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(entity));

            Optional<Shipment> result = dataStore.findByOrderId(ORDER_ID);

            assertThat(result).isPresent();
            Shipment shipment = result.get();
            assertThat(shipment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(shipment.getShippingAddress()).isEqualTo("456 Elm Ave, Portland, OR 97201");
            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(shipment.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("should return Optional.empty() when no entity is found for the given orderId")
        void shouldReturnEmptyWhenEntityNotFound() {
            when(repository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            Optional<Shipment> result = dataStore.findByOrderId(ORDER_ID);

            assertThat(result).isEmpty();
        }
    }

    /* ---------------------------------------------------------------------------------
     * update
     * --------------------------------------------------------------------------------- */

    @Nested
    @DisplayName("update")
    class Update {

        private static final String ORDER_ID = "order-003";
        private static final String ADDRESS = "789 Oak Blvd, Austin, TX 73301";
        private static final Instant CREATED_AT = Instant.parse("2026-09-01T12:00:00Z");

        @Test
        @DisplayName("should mutate entity status to CANCELLED before saving when entity is found")
        void shouldMutateEntityStatusBeforeSave() {
            ShipmentJpaEntity existingEntity = entityWith(ORDER_ID, ADDRESS, ShipmentStatus.CREATED, CREATED_AT);
            when(repository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingEntity));

            ShipmentJpaEntity updatedEntity = entityWith(ORDER_ID, ADDRESS, ShipmentStatus.CANCELLED, CREATED_AT);
            when(repository.save(existingEntity)).thenReturn(updatedEntity);

            Shipment domainWithCancelledStatus = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress(ADDRESS)
                    .status(ShipmentStatus.CANCELLED)
                    .createdAt(CREATED_AT)
                    .build();

            dataStore.update(domainWithCancelledStatus);

            ArgumentCaptor<ShipmentJpaEntity> captor = ArgumentCaptor.forClass(ShipmentJpaEntity.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("should return Optional with CANCELLED status domain object when entity is found")
        void shouldReturnDomainWithCancelledStatusWhenFound() {
            ShipmentJpaEntity existingEntity = entityWith(ORDER_ID, ADDRESS, ShipmentStatus.CREATED, CREATED_AT);
            when(repository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingEntity));

            ShipmentJpaEntity updatedEntity = entityWith(ORDER_ID, ADDRESS, ShipmentStatus.CANCELLED, CREATED_AT);
            when(repository.save(existingEntity)).thenReturn(updatedEntity);

            Shipment domainWithCancelledStatus = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress(ADDRESS)
                    .status(ShipmentStatus.CANCELLED)
                    .createdAt(CREATED_AT)
                    .build();

            Optional<Shipment> result = dataStore.update(domainWithCancelledStatus);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("should return Optional.empty() and never call repository.save when entity is not found")
        void shouldReturnEmptyAndNeverSaveWhenNotFound() {
            when(repository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            Shipment domain = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress(ADDRESS)
                    .status(ShipmentStatus.CANCELLED)
                    .createdAt(CREATED_AT)
                    .build();

            Optional<Shipment> result = dataStore.update(domain);

            assertThat(result).isEmpty();
            verify(repository, never()).save(any(ShipmentJpaEntity.class));
        }
    }
}
