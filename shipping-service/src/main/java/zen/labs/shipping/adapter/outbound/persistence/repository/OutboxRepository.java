/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.outbound.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.shipping.adapter.outbound.persistence.entity.OutboxJpaEntity;

public interface OutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {}
