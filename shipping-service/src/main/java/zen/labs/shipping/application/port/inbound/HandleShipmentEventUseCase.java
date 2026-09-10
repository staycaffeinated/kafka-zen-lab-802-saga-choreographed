/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.application.port.inbound;

public interface HandleShipmentEventUseCase {
    void handlePaymentProcessed(String orderId, String shippingAddress);

    void handleOrderCancellationRequested(String orderId);
}
