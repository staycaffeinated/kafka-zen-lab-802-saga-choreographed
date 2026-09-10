/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.port.inbound;

import java.math.BigDecimal;

public interface HandlePaymentEventUseCase {
    void handleInventoryReserved(String orderId, BigDecimal amount, String shippingAddress);

    void handleShipmentFailed(String orderId);

    void handleOrderCancellationRequested(String orderId);
}
