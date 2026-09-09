/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.saga;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import zen.labs.order.domain.SagaState;

@Schema(description = "Current state of a saga orchestration")
public record SagaStateResponse(
        @Schema(description = "Unique saga identifier") String sagaId,
        @Schema(description = "Associated order identifier") String orderId,

        @Schema(description = "Current saga status", example = "COMPLETED")
        String status,

        @Schema(description = "Current saga step", example = "DONE")
        String currentStep,

        @Schema(description = "Timestamp when saga started") Instant startedAt,

        @Schema(description = "Timestamp of most recent saga state change")
        Instant updatedAt) {

    public static SagaStateResponse from(SagaState saga) {
        return new SagaStateResponse(
                saga.getSagaId(),
                saga.getOrderId(),
                saga.getStatus().name(),
                saga.getCurrentStep().name(),
                saga.getStartedAt(),
                saga.getUpdatedAt());
    }
}
