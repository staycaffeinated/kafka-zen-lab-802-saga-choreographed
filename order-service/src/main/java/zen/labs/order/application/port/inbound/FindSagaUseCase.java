/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.inbound;

import zen.labs.order.domain.SagaState;

public interface FindSagaUseCase {
    SagaState findSaga(String sagaId);

    SagaState findSagaByOrderId(String orderId);
}
