/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.order.adapter.outbound.persistence.entity.IdempotencyKeyJpaEntity;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyJpaEntity, Long> {
    Optional<IdempotencyKeyJpaEntity> findByKey(String key);
}
