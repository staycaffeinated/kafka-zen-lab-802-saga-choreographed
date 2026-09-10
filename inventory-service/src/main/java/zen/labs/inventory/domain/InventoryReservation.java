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
    private final String orderId;

    @With
    private final ReservationStatus status;

    private final Instant reservedAt;

    public static InventoryReservation forOrder(String orderId) {
        return InventoryReservation.builder()
                .orderId(orderId)
                .status(ReservationStatus.RESERVED)
                .reservedAt(Instant.now())
                .build();
    }

    public InventoryReservation release() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot release reservation in status: " + status);
        }
        return withStatus(ReservationStatus.RELEASED);
    }
}
