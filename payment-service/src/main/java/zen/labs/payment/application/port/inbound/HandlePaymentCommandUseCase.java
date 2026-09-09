/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.port.inbound;

import java.math.BigDecimal;

public interface HandlePaymentCommandUseCase {
    void processPayment(String sagaId, String orderId, BigDecimal amount);

    void refundPayment(String sagaId, String orderId);
}
