/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
public class Shipment {
    private final String sagaId;
    private final String orderId;
    private final String shippingAddress;

    @With
    private final ShipmentStatus status;

    private final Instant createdAt;
}
