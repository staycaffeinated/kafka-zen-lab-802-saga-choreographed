/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import zen.labs.payment.adapter.outbound.persistence.entity.PaymentJpaEntity;
import zen.labs.payment.adapter.outbound.persistence.repository.PaymentRepository;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class PaymentJpaDataStoreTest {

    @Mock
    private PaymentRepository repository;

    @InjectMocks
    private PaymentJpaDataStore dataStore;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should persist entity with correct fields and map all fields back to domain")
        void shouldPersistEntityAndReturnMappedDomain() {
            // Given
            Instant processedAt = Instant.parse("2026-01-15T10:00:00Z");
            Payment domain = Payment.builder()
                    .sagaId("saga-001")
                    .orderId("order-001")
                    .amount(new BigDecimal("499.99"))
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(processedAt)
                    .build();

            PaymentJpaEntity savedEntity = new PaymentJpaEntity();
            savedEntity.setId(1L);
            savedEntity.setSagaId("saga-001");
            savedEntity.setOrderId("order-001");
            savedEntity.setAmount(new BigDecimal("499.99"));
            savedEntity.setStatus(PaymentStatus.PROCESSED);
            savedEntity.setProcessedAt(processedAt);

            when(repository.save(any(PaymentJpaEntity.class))).thenReturn(savedEntity);

            // When
            Payment result = dataStore.save(domain);

            // Then — verify the entity passed to the repository has correct fields
            ArgumentCaptor<PaymentJpaEntity> entityCaptor = ArgumentCaptor.forClass(PaymentJpaEntity.class);
            verify(repository).save(entityCaptor.capture());

            PaymentJpaEntity captured = entityCaptor.getValue();
            assertThat(captured.getSagaId()).isEqualTo("saga-001");
            assertThat(captured.getOrderId()).isEqualTo("order-001");
            assertThat(captured.getAmount()).isEqualByComparingTo(new BigDecimal("499.99"));
            assertThat(captured.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(captured.getProcessedAt()).isEqualTo(processedAt);

            // And verify the returned domain has all fields mapped back
            assertThat(result.getSagaId()).isEqualTo("saga-001");
            assertThat(result.getOrderId()).isEqualTo("order-001");
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("499.99"));
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(result.getProcessedAt()).isEqualTo(processedAt);
        }
    }

    @Nested
    @DisplayName("findBySagaId")
    class FindBySagaId {

        @Test
        @DisplayName("should return Optional with correct domain fields including amount when entity exists")
        void shouldReturnMappedDomain_whenEntityFound() {
            // Given
            Instant processedAt = Instant.parse("2026-02-20T08:30:00Z");
            PaymentJpaEntity entity = new PaymentJpaEntity();
            entity.setId(2L);
            entity.setSagaId("saga-002");
            entity.setOrderId("order-002");
            entity.setAmount(new BigDecimal("1250.00"));
            entity.setStatus(PaymentStatus.PROCESSED);
            entity.setProcessedAt(processedAt);

            when(repository.findBySagaId("saga-002")).thenReturn(Optional.of(entity));

            // When
            Optional<Payment> result = dataStore.findBySagaId("saga-002");

            // Then
            assertThat(result).isPresent();
            Payment payment = result.get();
            assertThat(payment.getSagaId()).isEqualTo("saga-002");
            assertThat(payment.getOrderId()).isEqualTo("order-002");
            assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("1250.00"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(payment.getProcessedAt()).isEqualTo(processedAt);
        }

        @Test
        @DisplayName("should return Optional.empty() when entity does not exist")
        void shouldReturnEmpty_whenEntityNotFound() {
            // Given
            when(repository.findBySagaId("saga-999")).thenReturn(Optional.empty());

            // When
            Optional<Payment> result = dataStore.findBySagaId("saga-999");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should mutate entity status to REFUNDED before save and return domain with REFUNDED status")
        void shouldMutateStatusAndReturnUpdatedDomain_whenEntityFound() {
            // Given
            Instant processedAt = Instant.parse("2026-03-10T14:00:00Z");

            PaymentJpaEntity existingEntity = new PaymentJpaEntity();
            existingEntity.setId(3L);
            existingEntity.setSagaId("saga-003");
            existingEntity.setOrderId("order-003");
            existingEntity.setAmount(new BigDecimal("750.00"));
            existingEntity.setStatus(PaymentStatus.PROCESSED);
            existingEntity.setProcessedAt(processedAt);

            Payment domainWithRefund = Payment.builder()
                    .sagaId("saga-003")
                    .orderId("order-003")
                    .amount(new BigDecimal("750.00"))
                    .status(PaymentStatus.REFUNDED)
                    .processedAt(processedAt)
                    .build();

            PaymentJpaEntity savedEntity = new PaymentJpaEntity();
            savedEntity.setId(3L);
            savedEntity.setSagaId("saga-003");
            savedEntity.setOrderId("order-003");
            savedEntity.setAmount(new BigDecimal("750.00"));
            savedEntity.setStatus(PaymentStatus.REFUNDED);
            savedEntity.setProcessedAt(processedAt);

            when(repository.findBySagaId("saga-003")).thenReturn(Optional.of(existingEntity));
            when(repository.save(existingEntity)).thenReturn(savedEntity);

            // When
            Optional<Payment> result = dataStore.update(domainWithRefund);

            // Then — verify the entity was mutated to REFUNDED before save
            assertThat(existingEntity.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(repository).save(existingEntity);

            // And the returned domain has REFUNDED status
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(result.get().getSagaId()).isEqualTo("saga-003");
        }

        @Test
        @DisplayName("should never call repository save and return Optional.empty() when entity not found")
        void shouldNeverSaveAndReturnEmpty_whenEntityNotFound() {
            // Given
            Payment domainWithRefund = Payment.builder()
                    .sagaId("saga-999")
                    .orderId("order-999")
                    .amount(new BigDecimal("100.00"))
                    .status(PaymentStatus.REFUNDED)
                    .processedAt(Instant.parse("2026-01-04T00:00:00Z"))
                    .build();

            when(repository.findBySagaId("saga-999")).thenReturn(Optional.empty());

            // When
            Optional<Payment> result = dataStore.update(domainWithRefund);

            // Then
            verify(repository, never()).save(any());
            assertThat(result).isEmpty();
        }
    }
}
