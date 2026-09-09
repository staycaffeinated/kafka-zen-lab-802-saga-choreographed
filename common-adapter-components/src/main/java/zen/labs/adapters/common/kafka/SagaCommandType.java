/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.kafka;

public enum SagaCommandType {
    RESERVE_INVENTORY,
    RELEASE_INVENTORY,
    PROCESS_PAYMENT,
    REFUND_PAYMENT,
    CREATE_SHIPMENT
}
