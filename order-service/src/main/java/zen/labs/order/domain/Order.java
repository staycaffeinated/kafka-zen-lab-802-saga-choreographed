/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
public class Order {

    private final String resourceId;
    private final String customerId;
    private final List<OrderItem> items;

    @With
    private final OrderStatus status;

    private final String shippingAddress;
    private final Instant createdAt;

    public BigDecimal totalAmount() {
        return items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
