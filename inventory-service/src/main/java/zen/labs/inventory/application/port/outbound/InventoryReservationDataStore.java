/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.application.port.outbound;

import java.util.Optional;
import zen.labs.inventory.domain.InventoryReservation;

public interface InventoryReservationDataStore {
    InventoryReservation save(InventoryReservation reservation);

    Optional<InventoryReservation> findBySagaId(String sagaId);

    Optional<InventoryReservation> update(InventoryReservation reservation);
}
