package zen.labs.order.application.port.inbound;

public interface HandleSagaEventUseCase {
    void onInventoryReservationFailed(String orderId);

    void onInventoryReleased(String orderId);

    void onShipmentCreated(String orderId);
}
