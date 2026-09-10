/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.outbound.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zen.labs.payment.adapter.outbound.persistence.entity.PaymentJpaEntity;

public interface PaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByOrderId(String orderId);
}
