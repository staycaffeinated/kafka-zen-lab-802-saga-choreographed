/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.outbound.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import zen.labs.payment.adapter.outbound.persistence.entity.PaymentJpaEntity;
import zen.labs.payment.adapter.outbound.persistence.repository.PaymentRepository;
import zen.labs.payment.application.port.outbound.PaymentDataStore;
import zen.labs.payment.domain.Payment;

@Component
@RequiredArgsConstructor
public class PaymentJpaDataStore implements PaymentDataStore {

    private final PaymentRepository repository;

    @Override
    public Payment save(Payment domain) {
        return toDomain(repository.save(toEntity(domain)));
    }

    @Override
    public Optional<Payment> findBySagaId(String sagaId) {
        return repository.findBySagaId(sagaId).map(this::toDomain);
    }

    @Override
    public Optional<Payment> update(Payment domain) {
        return repository
                .findBySagaId(domain.getSagaId())
                .map(entity -> {
                    entity.setStatus(domain.getStatus());
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    private Payment toDomain(PaymentJpaEntity e) {
        return Payment.builder()
                .sagaId(e.getSagaId())
                .orderId(e.getOrderId())
                .amount(e.getAmount())
                .status(e.getStatus())
                .processedAt(e.getProcessedAt())
                .build();
    }

    private PaymentJpaEntity toEntity(Payment d) {
        var e = new PaymentJpaEntity();
        e.setSagaId(d.getSagaId());
        e.setOrderId(d.getOrderId());
        e.setAmount(d.getAmount());
        e.setStatus(d.getStatus());
        e.setProcessedAt(d.getProcessedAt());
        return e;
    }
}
