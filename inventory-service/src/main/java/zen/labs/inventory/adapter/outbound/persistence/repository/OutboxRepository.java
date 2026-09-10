package zen.labs.inventory.adapter.outbound.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.inventory.adapter.outbound.persistence.entity.OutboxJpaEntity;

public interface OutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {}
