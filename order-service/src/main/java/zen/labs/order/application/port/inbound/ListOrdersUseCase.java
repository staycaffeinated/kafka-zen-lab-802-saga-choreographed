/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.inbound;

import java.util.List;
import zen.labs.order.domain.Order;

public interface ListOrdersUseCase {
    List<Order> listOrdersByCustomer(String customerId);
}
