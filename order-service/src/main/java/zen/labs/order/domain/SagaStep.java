/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

public enum SagaStep {
    RESERVE_INVENTORY,
    PROCESS_PAYMENT,
    CREATE_SHIPMENT,
    COMPENSATE_PAYMENT,
    COMPENSATE_INVENTORY,
    DONE
}
