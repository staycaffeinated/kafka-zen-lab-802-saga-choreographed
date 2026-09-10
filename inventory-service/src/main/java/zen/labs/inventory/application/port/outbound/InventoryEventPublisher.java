package zen.labs.inventory.application.port.outbound;

import java.math.BigDecimal;

public interface InventoryEventPublisher {
    void publishInventoryReserved(String orderId, BigDecimal amount, String shippingAddress);

    void publishInventoryReservationFailed(String orderId, String reason);

    void publishInventoryReleased(String orderId);
}
