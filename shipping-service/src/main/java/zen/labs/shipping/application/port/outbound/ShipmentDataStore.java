/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.application.port.outbound;

import java.util.Optional;
import zen.labs.shipping.domain.Shipment;

public interface ShipmentDataStore {
    Shipment save(Shipment shipment);

    Optional<Shipment> findBySagaId(String sagaId);

    Optional<Shipment> update(Shipment shipment);
}
