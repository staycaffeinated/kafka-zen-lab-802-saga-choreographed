/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderCommand(String idempotencyKey, String customerId, List<Item> items, String shippingAddress) {

    public record Item(String productId, int quantity, BigDecimal unitPrice) {}
}
