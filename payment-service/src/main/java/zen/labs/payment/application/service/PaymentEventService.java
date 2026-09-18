/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.payment.application.port.inbound.HandlePaymentEventUseCase;
import zen.labs.payment.application.port.outbound.PaymentDataStore;
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventService implements HandlePaymentEventUseCase {

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(10_000);

    private final PaymentDataStore paymentDataStore;
    private final PaymentEventPublisher eventPublisher;

    @Override
    @Transactional
    public void handleInventoryReserved(String orderId, BigDecimal amount, String shippingAddress) {
        if (paymentDataStore.findByOrderId(orderId).isPresent()) {
            log.warn("Duplicate INVENTORY_RESERVED event — payment already exists: orderId={}", orderId);
            return;
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            log.warn("Payment failed: orderId={} amount={}", orderId, amount);
            eventPublisher.publishPaymentFailed(orderId, "Amount exceeds maximum allowed: " + amount);
            return;
        }
        var payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.PROCESSED)
                .processedAt(Instant.now())
                .build();
        paymentDataStore.save(payment);
        log.info("===> Payment processed: orderId={}", orderId);
        eventPublisher.publishPaymentProcessed(orderId, shippingAddress);
    }

    @Override
    @Transactional
    public void handleShipmentFailed(String orderId) {
        var payment = paymentDataStore.findByOrderId(orderId);
        if (payment.isPresent()) {
            if (payment.get().getStatus() == PaymentStatus.REFUNDED) {
                log.warn("Duplicate SHIPMENT_FAILED event — payment already refunded: orderId={}", orderId);
                return;
            }
            paymentDataStore.update(payment.get().withStatus(PaymentStatus.REFUNDED));
            log.info("Payment refunded (shipment failed): orderId={}", orderId);
        } else {
            log.warn("No payment to refund: orderId={}", orderId);
        }
        eventPublisher.publishPaymentRefunded(orderId);
    }

    @Override
    @Transactional
    public void handleOrderCancellationRequested(String orderId) {
        var payment = paymentDataStore.findByOrderId(orderId);
        if (payment.isEmpty()) {
            log.debug("No payment to refund for cancelled order: orderId={}", orderId);
            return;
        }
        if (payment.get().getStatus() == PaymentStatus.REFUNDED) {
            log.warn("Duplicate ORDER_CANCELLATION_REQUESTED — payment already refunded: orderId={}", orderId);
            return;
        }
        paymentDataStore.update(payment.get().withStatus(PaymentStatus.REFUNDED));
        log.info("===> Payment refunded (order cancelled): orderId={}", orderId);
        eventPublisher.publishPaymentRefunded(orderId);
    }
}
