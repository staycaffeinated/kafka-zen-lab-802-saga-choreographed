/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.port.outbound;

public interface SagaReplyPublisher {
    void publish(String replyType, String sagaId, String orderId, boolean success, String failureReason);
}
