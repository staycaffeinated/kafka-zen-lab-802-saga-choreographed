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
import zen.labs.adapters.common.kafka.SagaReplyType;
import zen.labs.payment.application.port.inbound.HandlePaymentCommandUseCase;
import zen.labs.payment.application.port.outbound.PaymentDataStore;
import zen.labs.payment.application.port.outbound.SagaReplyPublisher;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaParticipant implements HandlePaymentCommandUseCase {

    private final PaymentDataStore paymentDataStore;
    private final SagaReplyPublisher replyPublisher;

    @Override
    public void processPayment(String sagaId, String orderId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(10_000)) > 0) {
            log.warn("Payment failed: sagaId={} amount={}", sagaId, amount);
            replyPublisher.publish(
                    SagaReplyType.PAYMENT_FAILED.name(),
                    sagaId,
                    orderId,
                    false,
                    "Amount exceeds maximum allowed: " + amount);
            return;
        }

        var payment = Payment.builder()
                .sagaId(sagaId)
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.PROCESSED)
                .processedAt(Instant.now())
                .build();
        paymentDataStore.save(payment);

        log.info("Payment processed: sagaId={} amount={}", sagaId, amount);
        replyPublisher.publish(SagaReplyType.PAYMENT_PROCESSED.name(), sagaId, orderId, true, null);
    }

    @Override
    public void refundPayment(String sagaId, String orderId) {
        paymentDataStore
                .findBySagaId(sagaId)
                .ifPresentOrElse(
                        p -> {
                            paymentDataStore.update(p.withStatus(PaymentStatus.REFUNDED));
                            log.info("Payment refunded: sagaId={} orderId={}", sagaId, orderId);
                        },
                        () -> log.warn("No payment found to refund: sagaId={}", sagaId));
        replyPublisher.publish(SagaReplyType.PAYMENT_REFUNDED.name(), sagaId, orderId, true, null);
    }
}
