/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.inbound.FindSagaUseCase;
import zen.labs.order.application.port.outbound.SagaStateDataStore;
import zen.labs.order.domain.SagaState;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SagaQueryService implements FindSagaUseCase {

    private final SagaStateDataStore sagaStateDataStore;

    @Override
    public SagaState findSaga(String sagaId) {
        return sagaStateDataStore
                .findBySagaId(sagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found: " + sagaId));
    }

    @Override
    public SagaState findSagaByOrderId(String orderId) {
        return sagaStateDataStore
                .findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found for order: " + orderId));
    }
}
