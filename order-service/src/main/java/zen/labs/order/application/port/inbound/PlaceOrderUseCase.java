/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.inbound;

import zen.labs.order.domain.Order;

public interface PlaceOrderUseCase {
    Order placeOrder(PlaceOrderCommand command);
}
