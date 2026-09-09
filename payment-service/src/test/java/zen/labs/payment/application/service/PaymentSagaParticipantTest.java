/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.service;

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
import zen.labs.payment.application.port.outbound.PaymentDataStore;
import zen.labs.payment.application.port.outbound.SagaReplyPublisher;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class PaymentSagaParticipantTest {

    @Mock
    private PaymentDataStore paymentDataStore;

    @Mock
    private SagaReplyPublisher replyPublisher;

    @InjectMocks
    private PaymentSagaParticipant participant;

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName(
                "should save payment with PROCESSED status and publish PAYMENT_PROCESSED when amount is below limit")
        void shouldSaveAndPublishSuccess_whenAmountBelowLimit() {
            // Given
            String sagaId = "saga-001";
            String orderId = "order-001";
            BigDecimal amount = new BigDecimal("9999.99");

            Payment savedPayment = Payment.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .amount(amount)
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(paymentDataStore.save(any(Payment.class))).thenReturn(savedPayment);

            // When
            participant.processPayment(sagaId, orderId, amount);

            // Then
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentDataStore).save(paymentCaptor.capture());

            Payment captured = paymentCaptor.getValue();
            assertThat(captured.getSagaId()).isEqualTo(sagaId);
            assertThat(captured.getOrderId()).isEqualTo(orderId);
            assertThat(captured.getAmount()).isEqualByComparingTo(amount);
            assertThat(captured.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(captured.getProcessedAt()).isNotNull();

            verify(replyPublisher).publish("PAYMENT_PROCESSED", sagaId, orderId, true, null);
        }

        @Test
        @DisplayName("should succeed and save payment when amount is exactly 10000 (boundary — compareTo > 0 is false)")
        void shouldSaveAndPublishSuccess_whenAmountIsExactly10000() {
            // Given
            String sagaId = "saga-002";
            String orderId = "order-002";
            BigDecimal amount = new BigDecimal("10000");

            Payment savedPayment = Payment.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .amount(amount)
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(paymentDataStore.save(any(Payment.class))).thenReturn(savedPayment);

            // When
            participant.processPayment(sagaId, orderId, amount);

            // Then
            verify(paymentDataStore).save(any(Payment.class));
            verify(replyPublisher).publish("PAYMENT_PROCESSED", sagaId, orderId, true, null);
        }

        @Test
        @DisplayName(
                "should NOT save and should publish PAYMENT_FAILED with amount in reason when amount exceeds 10000")
        void shouldNotSaveAndPublishFailure_whenAmountExceedsLimit() {
            // Given
            String sagaId = "saga-003";
            String orderId = "order-003";
            BigDecimal amount = new BigDecimal("10000.01");

            // When
            participant.processPayment(sagaId, orderId, amount);

            // Then
            verify(paymentDataStore, never()).save(any());
            verify(replyPublisher)
                    .publish("PAYMENT_FAILED", sagaId, orderId, false, "Amount exceeds maximum allowed: " + amount);
        }

        @Test
        @DisplayName("should fail and publish PAYMENT_FAILED when amount is exactly 10001")
        void shouldNotSaveAndPublishFailure_whenAmountIsExactly10001() {
            // Given
            String sagaId = "saga-004";
            String orderId = "order-004";
            BigDecimal amount = new BigDecimal("10001");

            // When
            participant.processPayment(sagaId, orderId, amount);

            // Then
            verify(paymentDataStore, never()).save(any());
            verify(replyPublisher)
                    .publish("PAYMENT_FAILED", sagaId, orderId, false, "Amount exceeds maximum allowed: " + amount);
        }
    }

    @Nested
    @DisplayName("refundPayment")
    class RefundPayment {

        @Test
        @DisplayName("should update payment to REFUNDED status and publish PAYMENT_REFUNDED when payment found")
        void shouldUpdateToRefundedAndPublish_whenPaymentFound() {
            // Given
            String sagaId = "saga-005";
            String orderId = "order-005";
            Payment existing = Payment.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .amount(new BigDecimal("500.00"))
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(paymentDataStore.findBySagaId(sagaId)).thenReturn(Optional.of(existing));

            // When
            participant.refundPayment(sagaId, orderId);

            // Then
            ArgumentCaptor<Payment> updateCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentDataStore).update(updateCaptor.capture());

            Payment updatedArg = updateCaptor.getValue();
            assertThat(updatedArg.getSagaId()).isEqualTo(sagaId);
            assertThat(updatedArg.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

            verify(replyPublisher).publish("PAYMENT_REFUNDED", sagaId, orderId, true, null);
        }

        @Test
        @DisplayName("should still publish PAYMENT_REFUNDED and never call update when payment not found")
        void shouldPublishWithoutUpdate_whenPaymentNotFound() {
            // Given
            String sagaId = "saga-006";
            String orderId = "order-006";
            when(paymentDataStore.findBySagaId(sagaId)).thenReturn(Optional.empty());

            // When
            participant.refundPayment(sagaId, orderId);

            // Then
            verify(paymentDataStore, never()).update(any());
            verify(replyPublisher).publish("PAYMENT_REFUNDED", sagaId, orderId, true, null);
        }
    }
}
