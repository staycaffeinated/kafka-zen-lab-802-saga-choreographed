/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<CreateOrderItemRequest> items,
        @NotBlank String shippingAddress) {}
