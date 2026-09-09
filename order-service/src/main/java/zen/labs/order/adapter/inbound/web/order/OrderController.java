/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.inbound.FindOrderUseCase;
import zen.labs.order.application.port.inbound.ListOrdersUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Place and query orders")
@SuppressWarnings({"java:S1710" // false positive.
})
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final FindOrderUseCase findOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderController(
            PlaceOrderUseCase placeOrderUseCase,
            FindOrderUseCase findOrderUseCase,
            ListOrdersUseCase listOrdersUseCase,
            CancelOrderUseCase cancelOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.findOrderUseCase = findOrderUseCase;
        this.listOrdersUseCase = listOrdersUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Place an order",
            description = "Starts a new order saga. Supports idempotent submission via the Idempotency-Key header.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Order created",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content)
    })
    public OrderResponse placeOrder(
            @Parameter(description = "Optional client-generated key for idempotent submission")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        var command = new PlaceOrderCommand(
                idempotencyKey,
                request.customerId(),
                request.items().stream()
                        .map(i -> new PlaceOrderCommand.Item(i.productId(), i.quantity(), i.unitPrice()))
                        .toList(),
                request.shippingAddress());
        return OrderResponse.from(placeOrderUseCase.placeOrder(command));
    }

    @GetMapping("/{resourceId}")
    @Operation(summary = "Get an order", description = "Returns the order with the given resource identifier.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Order found",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
    })
    public OrderResponse getOrder(
            @Parameter(description = "Order resource identifier", required = true) @PathVariable String resourceId) {
        return OrderResponse.from(findOrderUseCase.findOrder(resourceId));
    }

    @PostMapping("/{resourceId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancel an order",
            description =
                    "Triggers compensation for an in-progress order. Only valid when the saga has reached a stable compensation point (inventory reserved or payment processed).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cancellation initiated", content = @Content),
        @ApiResponse(responseCode = "404", description = "Order not found", content = @Content),
        @ApiResponse(responseCode = "422", description = "Order not in a cancellable state", content = @Content)
    })
    public void cancelOrder(
            @Parameter(description = "Order resource identifier", required = true) @PathVariable String resourceId) {
        cancelOrderUseCase.cancelOrder(resourceId);
    }

    @GetMapping
    @Operation(
            summary = "List orders by customer",
            description = "Returns all orders placed by the specified customer.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Orders retrieved",
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid customerId parameter", content = @Content)
    })
    public List<OrderResponse> listOrders(
            @Parameter(description = "Customer identifier", required = true) @RequestParam String customerId) {
        return listOrdersUseCase.listOrdersByCustomer(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
