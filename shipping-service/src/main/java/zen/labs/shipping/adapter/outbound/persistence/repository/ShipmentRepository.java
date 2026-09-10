/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.outbound.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.shipping.adapter.outbound.persistence.entity.ShipmentJpaEntity;

public interface ShipmentRepository extends JpaRepository<ShipmentJpaEntity, Long> {
    Optional<ShipmentJpaEntity> findByOrderId(String orderId);
}
