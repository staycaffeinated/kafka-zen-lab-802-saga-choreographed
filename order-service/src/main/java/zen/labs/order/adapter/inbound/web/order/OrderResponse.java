/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import zen.labs.order.domain.Order;

public record OrderResponse(
        String resourceId,
        String customerId,
        String status,
        String shippingAddress,
        BigDecimal totalAmount,
        List<Item> items,
        Instant createdAt) {

    public record Item(String productId, int quantity, BigDecimal unitPrice) {}

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getResourceId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getShippingAddress(),
                order.totalAmount(),
                order.getItems().stream()
                        .map(i -> new Item(i.productId(), i.quantity(), i.unitPrice()))
                        .toList(),
                order.getCreatedAt());
    }
}
