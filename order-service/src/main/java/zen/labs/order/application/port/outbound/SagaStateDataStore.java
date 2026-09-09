/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.outbound;

import java.util.Optional;
import zen.labs.order.domain.SagaState;

public interface SagaStateDataStore {
    SagaState save(SagaState sagaState);

    Optional<SagaState> findBySagaId(String sagaId);

    Optional<SagaState> findByOrderId(String orderId);

    Optional<SagaState> update(SagaState sagaState);
}
