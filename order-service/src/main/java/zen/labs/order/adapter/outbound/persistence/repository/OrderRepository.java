/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.order.adapter.outbound.persistence.entity.OrderJpaEntity;

public interface OrderRepository extends JpaRepository<OrderJpaEntity, Long> {
    Optional<OrderJpaEntity> findByResourceId(String resourceId);

    List<OrderJpaEntity> findByCustomerId(String customerId);
}
