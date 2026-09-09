/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import zen.labs.order.adapter.outbound.persistence.entity.SagaStateJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.SagaStateRepository;
import zen.labs.order.application.port.outbound.SagaStateDataStore;
import zen.labs.order.domain.SagaState;

@Component
@RequiredArgsConstructor
public class SagaStateJpaDataStore implements SagaStateDataStore {

    private final SagaStateRepository sagaStateRepository;

    @Override
    public SagaState save(SagaState domain) {
        return toDomain(sagaStateRepository.save(toEntity(domain)));
    }

    @Override
    public Optional<SagaState> findBySagaId(String sagaId) {
        return sagaStateRepository.findBySagaId(sagaId).map(this::toDomain);
    }

    @Override
    public Optional<SagaState> findByOrderId(String orderId) {
        return sagaStateRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Optional<SagaState> update(SagaState domain) {
        return sagaStateRepository
                .findBySagaId(domain.getSagaId())
                .map(entity -> {
                    entity.setStatus(domain.getStatus());
                    entity.setCurrentStep(domain.getCurrentStep());
                    entity.setUpdatedAt(domain.getUpdatedAt());
                    return sagaStateRepository.save(entity);
                })
                .map(this::toDomain);
    }

    private SagaState toDomain(SagaStateJpaEntity entity) {
        return SagaState.builder()
                .sagaId(entity.getSagaId())
                .orderId(entity.getOrderId())
                .status(entity.getStatus())
                .currentStep(entity.getCurrentStep())
                .startedAt(entity.getStartedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private SagaStateJpaEntity toEntity(SagaState domain) {
        var entity = new SagaStateJpaEntity();
        entity.setSagaId(domain.getSagaId());
        entity.setOrderId(domain.getOrderId());
        entity.setStatus(domain.getStatus());
        entity.setCurrentStep(domain.getCurrentStep());
        entity.setStartedAt(domain.getStartedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
