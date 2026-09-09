/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
public class InventoryReservation {
    private final String sagaId;
    private final String orderId;

    @With
    private final ReservationStatus status;

    private final Instant reservedAt;
}
