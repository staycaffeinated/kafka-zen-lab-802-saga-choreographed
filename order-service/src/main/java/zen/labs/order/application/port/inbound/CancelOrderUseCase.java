/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.inbound;

public interface CancelOrderUseCase {
    void cancelOrder(String orderId);
}
