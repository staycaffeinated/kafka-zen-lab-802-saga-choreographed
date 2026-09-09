/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

public enum SagaStatus {
    STARTED,
    INVENTORY_RESERVED,
    PAYMENT_PROCESSED,
    COMPLETED,
    COMPENSATING,
    FAILED;

    public boolean canTransitionTo(SagaStatus next) {
        return switch (this) {
            case STARTED -> next == INVENTORY_RESERVED || next == FAILED;
            case INVENTORY_RESERVED -> next == PAYMENT_PROCESSED || next == COMPENSATING;
            case PAYMENT_PROCESSED -> next == COMPLETED || next == COMPENSATING;
            case COMPENSATING -> next == COMPENSATING || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
