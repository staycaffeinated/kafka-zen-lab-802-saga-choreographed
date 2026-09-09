/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.order.adapter.outbound.persistence.entity.SagaStateJpaEntity;

public interface SagaStateRepository extends JpaRepository<SagaStateJpaEntity, Long> {
    Optional<SagaStateJpaEntity> findBySagaId(String sagaId);

    Optional<SagaStateJpaEntity> findByOrderId(String orderId);
}
