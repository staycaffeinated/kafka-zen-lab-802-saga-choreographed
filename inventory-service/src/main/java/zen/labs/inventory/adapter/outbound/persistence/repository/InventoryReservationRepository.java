/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.outbound.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.inventory.adapter.outbound.persistence.entity.InventoryReservationJpaEntity;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationJpaEntity, Long> {
    Optional<InventoryReservationJpaEntity> findByOrderId(String orderId);
}
