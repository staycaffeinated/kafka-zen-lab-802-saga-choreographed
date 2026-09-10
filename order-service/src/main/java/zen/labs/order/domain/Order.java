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

    public Order startCancellation() {
        if (status != OrderStatus.CONFIRMING) {
            throw new IllegalStateException("Cannot start cancellation for order in status: " + status);
        }
        return withStatus(OrderStatus.CANCELLING);
    }

    public Order cancel() {
        if (status != OrderStatus.CONFIRMING && status != OrderStatus.CANCELLING) {
            throw new IllegalStateException("Cannot cancel order in status: " + status);
        }
        return withStatus(OrderStatus.CANCELLED);
    }

    public Order confirm() {
        if (status != OrderStatus.CONFIRMING) {
            throw new IllegalStateException("Cannot confirm order in status: " + status);
        }
        return withStatus(OrderStatus.CONFIRMED);
    }

    public boolean isConfirming() {
        return status == OrderStatus.CONFIRMING;
    }

    public boolean isConfirmed() {
        return status == OrderStatus.CONFIRMED;
    }
}
