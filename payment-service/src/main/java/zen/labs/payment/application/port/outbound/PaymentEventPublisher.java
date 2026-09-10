/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.port.outbound;

public interface PaymentEventPublisher {
    void publishPaymentProcessed(String orderId, String shippingAddress);

    void publishPaymentFailed(String orderId, String reason);

    void publishPaymentRefunded(String orderId);
}
