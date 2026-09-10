/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.application.port.outbound;

public interface ShippingEventPublisher {
    void publishShipmentCreated(String orderId);

    void publishShipmentFailed(String orderId, String reason);
}
