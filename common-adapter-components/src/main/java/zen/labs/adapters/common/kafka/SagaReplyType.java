/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.kafka;

public enum SagaReplyType {
    INVENTORY_RESERVED,
    INVENTORY_RESERVATION_FAILED,
    INVENTORY_RELEASED,
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    SHIPMENT_CREATED,
    SHIPMENT_FAILED
}
