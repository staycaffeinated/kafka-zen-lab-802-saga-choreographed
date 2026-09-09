/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.application.port.inbound;

public interface HandleShipmentCommandUseCase {
    void createShipment(String sagaId, String orderId, String shippingAddress);
}
