/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.outbound.messaging;

public record SagaReply(String sagaId, String orderId, String replyType, boolean success, String failureReason) {}
