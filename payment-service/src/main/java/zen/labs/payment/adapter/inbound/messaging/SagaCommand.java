/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.inbound.messaging;

import java.math.BigDecimal;
import java.util.List;

public record SagaCommand(
        String sagaId,
        String orderId,
        String commandType,
        List<SagaCommandItem> items,
        BigDecimal amount,
        String shippingAddress) {}
