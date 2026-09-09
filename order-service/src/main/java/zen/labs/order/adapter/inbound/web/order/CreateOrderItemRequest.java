/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderItemRequest(
        @NotBlank String productId,
        @Positive int quantity,
        @Positive BigDecimal unitPrice) {}
