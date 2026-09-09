/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.messaging;

public record SagaCommandItem(String productId, int quantity) {}
