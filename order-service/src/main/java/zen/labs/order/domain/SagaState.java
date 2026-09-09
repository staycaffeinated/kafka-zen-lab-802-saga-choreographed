/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
public class SagaState {

    private final String sagaId;
    private final String orderId;

    @With
    private final SagaStatus status;

    @With
    private final SagaStep currentStep;

    private final Instant startedAt;

    @With
    private final Instant updatedAt;

    public SagaState transitionTo(SagaStatus newStatus, SagaStep newStep, Instant now) {
        if (!status.canTransitionTo(newStatus)) {
            throw new SagaDomainException(
                    "Invalid saga transition: " + status + " -> " + newStatus + " (sagaId=" + sagaId + ")");
        }
        return withStatus(newStatus).withCurrentStep(newStep).withUpdatedAt(now);
    }
}
