/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.saga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zen.labs.order.application.port.inbound.FindSagaUseCase;

@RestController
@RequestMapping("/api/v1/sagas")
@Tag(name = "Sagas", description = "Query saga orchestration state")
@SuppressWarnings({"java:S1710" // false positive.
})
public class SagaController {

    private final FindSagaUseCase findSagaUseCase;

    public SagaController(FindSagaUseCase findSagaUseCase) {
        this.findSagaUseCase = findSagaUseCase;
    }

    @GetMapping("/{sagaId}")
    @Operation(
            summary = "Get saga state",
            description = "Returns the current orchestration state of a saga by its identifier.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Saga found",
                content = @Content(schema = @Schema(implementation = SagaStateResponse.class))),
        @ApiResponse(responseCode = "404", description = "Saga not found", content = @Content)
    })
    public SagaStateResponse getSaga(
            @Parameter(description = "Saga identifier", required = true) @PathVariable String sagaId) {
        return SagaStateResponse.from(findSagaUseCase.findSaga(sagaId));
    }

    @GetMapping
    @Operation(
            summary = "Find saga by order",
            description = "Returns the saga orchestration state for the given order identifier.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Saga found",
                content = @Content(schema = @Schema(implementation = SagaStateResponse.class))),
        @ApiResponse(responseCode = "404", description = "Saga not found", content = @Content)
    })
    public SagaStateResponse getSagaByOrderId(
            @Parameter(description = "Order resource identifier", required = true) @RequestParam String orderId) {
        return SagaStateResponse.from(findSagaUseCase.findSagaByOrderId(orderId));
    }
}
