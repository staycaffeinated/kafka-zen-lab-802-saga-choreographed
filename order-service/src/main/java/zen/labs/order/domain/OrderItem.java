/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

import java.math.BigDecimal;

public record OrderItem(String productId, int quantity, BigDecimal unitPrice) {}
