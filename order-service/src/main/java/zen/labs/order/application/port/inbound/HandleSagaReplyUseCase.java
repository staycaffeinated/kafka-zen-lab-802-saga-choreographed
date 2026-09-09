/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.inbound;

public interface HandleSagaReplyUseCase {
    void onInventoryReply(String sagaId, String orderId, boolean success, String failureReason);

    void onPaymentReply(String sagaId, String orderId, boolean success, String failureReason);

    void onShipmentReply(String sagaId, String orderId, boolean success, String failureReason);

    void onInventoryReleasedReply(String sagaId, String orderId);

    void onPaymentRefundedReply(String sagaId, String orderId);
}
