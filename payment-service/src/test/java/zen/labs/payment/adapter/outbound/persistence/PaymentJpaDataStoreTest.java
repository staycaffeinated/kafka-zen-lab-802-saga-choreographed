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
            Instant processedAt = Instant.parse("2026-01-15T10:00:00Z");
            Payment domain = Payment.builder()
                    .orderId("order-001")
                    .amount(new BigDecimal("499.99"))
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(processedAt)
                    .build();

            PaymentJpaEntity savedEntity = new PaymentJpaEntity();
            savedEntity.setId(1L);
            savedEntity.setOrderId("order-001");
            savedEntity.setAmount(new BigDecimal("499.99"));
            savedEntity.setStatus(PaymentStatus.PROCESSED);
            savedEntity.setProcessedAt(processedAt);

            when(repository.save(any(PaymentJpaEntity.class))).thenReturn(savedEntity);

            Payment result = dataStore.save(domain);

            ArgumentCaptor<PaymentJpaEntity> entityCaptor = ArgumentCaptor.forClass(PaymentJpaEntity.class);
            verify(repository).save(entityCaptor.capture());

            PaymentJpaEntity captured = entityCaptor.getValue();
            assertThat(captured.getOrderId()).isEqualTo("order-001");
            assertThat(captured.getAmount()).isEqualByComparingTo(new BigDecimal("499.99"));
            assertThat(captured.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(captured.getProcessedAt()).isEqualTo(processedAt);

            assertThat(result.getOrderId()).isEqualTo("order-001");
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("499.99"));
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(result.getProcessedAt()).isEqualTo(processedAt);
        }
    }

    @Nested
    @DisplayName("findByOrderId")
    class FindByOrderId {

        @Test
        @DisplayName("should return Optional with correct domain fields when entity exists")
        void shouldReturnMappedDomain_whenEntityFound() {
            Instant processedAt = Instant.parse("2026-02-20T08:30:00Z");
            PaymentJpaEntity entity = new PaymentJpaEntity();
            entity.setId(2L);
            entity.setOrderId("order-002");
            entity.setAmount(new BigDecimal("1250.00"));
            entity.setStatus(PaymentStatus.PROCESSED);
            entity.setProcessedAt(processedAt);

            when(repository.findByOrderId("order-002")).thenReturn(Optional.of(entity));

            Optional<Payment> result = dataStore.findByOrderId("order-002");

            assertThat(result).isPresent();
            Payment payment = result.get();
            assertThat(payment.getOrderId()).isEqualTo("order-002");
            assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("1250.00"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(payment.getProcessedAt()).isEqualTo(processedAt);
        }

        @Test
        @DisplayName("should return Optional.empty() when entity does not exist")
        void shouldReturnEmpty_whenEntityNotFound() {
            when(repository.findByOrderId("order-999")).thenReturn(Optional.empty());

            Optional<Payment> result = dataStore.findByOrderId("order-999");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should mutate entity status to REFUNDED before save and return domain with REFUNDED status")
        void shouldMutateStatusAndReturnUpdatedDomain_whenEntityFound() {
            Instant processedAt = Instant.parse("2026-03-10T14:00:00Z");

            PaymentJpaEntity existingEntity = new PaymentJpaEntity();
            existingEntity.setId(3L);
            existingEntity.setOrderId("order-003");
            existingEntity.setAmount(new BigDecimal("750.00"));
            existingEntity.setStatus(PaymentStatus.PROCESSED);
            existingEntity.setProcessedAt(processedAt);

            Payment domainWithRefund = Payment.builder()
                    .orderId("order-003")
                    .amount(new BigDecimal("750.00"))
                    .status(PaymentStatus.REFUNDED)
                    .processedAt(processedAt)
                    .build();

            PaymentJpaEntity savedEntity = new PaymentJpaEntity();
            savedEntity.setId(3L);
            savedEntity.setOrderId("order-003");
            savedEntity.setAmount(new BigDecimal("750.00"));
            savedEntity.setStatus(PaymentStatus.REFUNDED);
            savedEntity.setProcessedAt(processedAt);

            when(repository.findByOrderId("order-003")).thenReturn(Optional.of(existingEntity));
            when(repository.save(existingEntity)).thenReturn(savedEntity);

            Optional<Payment> result = dataStore.update(domainWithRefund);

            assertThat(existingEntity.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(repository).save(existingEntity);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(result.get().getOrderId()).isEqualTo("order-003");
        }

        @Test
        @DisplayName("should never call repository save and return Optional.empty() when entity not found")
        void shouldNeverSaveAndReturnEmpty_whenEntityNotFound() {
            Payment domainWithRefund = Payment.builder()
                    .orderId("order-999")
                    .amount(new BigDecimal("100.00"))
                    .status(PaymentStatus.REFUNDED)
                    .processedAt(Instant.parse("2026-01-04T00:00:00Z"))
                    .build();

            when(repository.findByOrderId("order-999")).thenReturn(Optional.empty());

            Optional<Payment> result = dataStore.update(domainWithRefund);

            verify(repository, never()).save(any());
            assertThat(result).isEmpty();
        }
    }
}
