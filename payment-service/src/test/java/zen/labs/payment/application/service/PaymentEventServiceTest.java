package zen.labs.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventService")
class PaymentEventServiceTest {

    @Mock
    private PaymentDataStore paymentDataStore;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentEventService service;

    private static final String ORDER_ID = "order-001";
    private static final String SHIPPING_ADDRESS = "123 Main St";

    @Nested
    @DisplayName("handleInventoryReserved")
    class HandleInventoryReserved {

        @Test
        @DisplayName("should save payment and publish PAYMENT_PROCESSED with shippingAddress when amount within limit")
        void shouldProcessAndPublish_whenWithinLimit() {
            when(paymentDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleInventoryReserved(ORDER_ID, BigDecimal.valueOf(5_000), SHIPPING_ADDRESS);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            verify(eventPublisher).publishPaymentProcessed(ORDER_ID, SHIPPING_ADDRESS);
        }

        @Test
        @DisplayName("should skip save and publish when payment already exists (idempotent duplicate)")
        void shouldSkipSaveAndPublish_whenPaymentAlreadyExists() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID)
                    .amount(BigDecimal.valueOf(50))
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleInventoryReserved(ORDER_ID, BigDecimal.valueOf(50), SHIPPING_ADDRESS);

            verify(paymentDataStore, never()).save(any());
            verify(eventPublisher, never()).publishPaymentProcessed(any(), any());
            verify(eventPublisher, never()).publishPaymentFailed(any(), any());
        }

        @Test
        @DisplayName("should NOT save and publish PAYMENT_FAILED when amount exceeds 10000")
        void shouldFailAndPublish_whenAmountExceedsLimit() {
            service.handleInventoryReserved(ORDER_ID, BigDecimal.valueOf(10_001), SHIPPING_ADDRESS);

            verify(paymentDataStore, never()).save(any());
            verify(eventPublisher).publishPaymentFailed(eq(ORDER_ID), any());
        }

        @Test
        @DisplayName("amount of exactly 10000 should succeed")
        void shouldSucceed_atExactLimit() {
            when(paymentDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleInventoryReserved(ORDER_ID, BigDecimal.valueOf(10_000), SHIPPING_ADDRESS);

            verify(eventPublisher).publishPaymentProcessed(ORDER_ID, SHIPPING_ADDRESS);
        }
    }

    @Nested
    @DisplayName("handleShipmentFailed")
    class HandleShipmentFailed {

        @Test
        @DisplayName("should refund payment and publish PAYMENT_REFUNDED when payment found")
        void shouldRefundAndPublish_whenPaymentFound() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID)
                    .amount(BigDecimal.TEN)
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(Instant.parse("2026-02-20T00:00:00Z"))
                    .build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleShipmentFailed(ORDER_ID);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(eventPublisher).publishPaymentRefunded(ORDER_ID);
        }

        @Test
        @DisplayName("should still publish PAYMENT_REFUNDED when no payment found")
        void shouldStillPublish_whenNoPayment() {
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleShipmentFailed(ORDER_ID);

            verify(paymentDataStore, never()).update(any());
            verify(eventPublisher).publishPaymentRefunded(ORDER_ID);
        }

        @Test
        @DisplayName("should skip update and publish when payment already refunded (idempotent duplicate)")
        void shouldSkip_whenPaymentAlreadyRefunded() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID)
                    .amount(BigDecimal.TEN)
                    .status(PaymentStatus.REFUNDED)
                    .processedAt(Instant.parse("2026-02-20T00:00:00Z"))
                    .build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleShipmentFailed(ORDER_ID);

            verify(paymentDataStore, never()).update(any());
            verify(eventPublisher, never()).publishPaymentRefunded(any());
        }
    }

    @Nested
    @DisplayName("handleOrderCancellationRequested")
    class HandleOrderCancellationRequested {

        @Test
        @DisplayName("should refund payment silently when payment exists (no event)")
        void shouldRefund_whenPaymentFound() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID)
                    .amount(BigDecimal.TEN)
                    .status(PaymentStatus.PROCESSED)
                    .processedAt(Instant.parse("2026-02-20T00:00:00Z"))
                    .build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderCancellationRequested(ORDER_ID);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(eventPublisher, never()).publishPaymentRefunded(any());
        }

        @Test
        @DisplayName("should do nothing when no payment exists")
        void shouldDoNothing_whenNoPayment() {
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(paymentDataStore, never()).update(any());
            verify(eventPublisher, never()).publishPaymentRefunded(any());
        }

        @Test
        @DisplayName("should skip update when payment already refunded (idempotent duplicate)")
        void shouldSkip_whenPaymentAlreadyRefunded() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID)
                    .amount(BigDecimal.TEN)
                    .status(PaymentStatus.REFUNDED)
                    .processedAt(Instant.parse("2026-02-20T00:00:00Z"))
                    .build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(paymentDataStore, never()).update(any());
            verify(eventPublisher, never()).publishPaymentRefunded(any());
        }
    }
}
