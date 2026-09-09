/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.outbound;

import java.math.BigDecimal;
import java.util.List;
import zen.labs.order.domain.OrderItem;

public interface SagaCommandPublisher {
    void reserveInventory(String sagaId, String orderId, List<OrderItem> items);

    void processPayment(String sagaId, String orderId, BigDecimal amount);

    void createShipment(String sagaId, String orderId, String shippingAddress);

    void releaseInventory(String sagaId, String orderId);

    void refundPayment(String sagaId, String orderId);
}
