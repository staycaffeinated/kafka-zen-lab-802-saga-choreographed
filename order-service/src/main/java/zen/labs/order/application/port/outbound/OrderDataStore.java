/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.outbound;

import java.util.List;
import zen.labs.order.domain.Order;

public interface OrderDataStore extends DataStore<Order> {
    List<Order> findByCustomerId(String customerId);
}
