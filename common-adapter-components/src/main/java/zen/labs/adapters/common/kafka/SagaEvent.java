package zen.labs.adapters.common.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SagaEvent.OrderPlacedEvent.class, name = "ORDER_PLACED"),
    @JsonSubTypes.Type(value = SagaEvent.OrderCancellationRequestedEvent.class, name = "ORDER_CANCELLATION_REQUESTED"),
    @JsonSubTypes.Type(value = SagaEvent.InventoryReservedEvent.class, name = "INVENTORY_RESERVED"),
    @JsonSubTypes.Type(value = SagaEvent.InventoryReservationFailedEvent.class, name = "INVENTORY_RESERVATION_FAILED"),
    @JsonSubTypes.Type(value = SagaEvent.InventoryReleasedEvent.class, name = "INVENTORY_RELEASED"),
    @JsonSubTypes.Type(value = SagaEvent.PaymentProcessedEvent.class, name = "PAYMENT_PROCESSED"),
    @JsonSubTypes.Type(value = SagaEvent.PaymentFailedEvent.class, name = "PAYMENT_FAILED"),
    @JsonSubTypes.Type(value = SagaEvent.PaymentRefundedEvent.class, name = "PAYMENT_REFUNDED"),
    @JsonSubTypes.Type(value = SagaEvent.ShipmentCreatedEvent.class, name = "SHIPMENT_CREATED"),
    @JsonSubTypes.Type(value = SagaEvent.ShipmentFailedEvent.class, name = "SHIPMENT_FAILED")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface SagaEvent {

    String eventId();

    String orderId();

    record OrderPlacedEvent(
            String eventId, String orderId, List<SagaEventItem> items, BigDecimal amount, String shippingAddress)
            implements SagaEvent {}

    record OrderCancellationRequestedEvent(String eventId, String orderId) implements SagaEvent {}

    record InventoryReservedEvent(String eventId, String orderId, BigDecimal amount, String shippingAddress)
            implements SagaEvent {}

    record InventoryReservationFailedEvent(String eventId, String orderId, String failureReason) implements SagaEvent {}

    record InventoryReleasedEvent(String eventId, String orderId) implements SagaEvent {}

    record PaymentProcessedEvent(String eventId, String orderId, String shippingAddress) implements SagaEvent {}

    record PaymentFailedEvent(String eventId, String orderId, String failureReason) implements SagaEvent {}

    record PaymentRefundedEvent(String eventId, String orderId) implements SagaEvent {}

    record ShipmentCreatedEvent(String eventId, String orderId) implements SagaEvent {}

    record ShipmentFailedEvent(String eventId, String orderId, String failureReason) implements SagaEvent {}
}
