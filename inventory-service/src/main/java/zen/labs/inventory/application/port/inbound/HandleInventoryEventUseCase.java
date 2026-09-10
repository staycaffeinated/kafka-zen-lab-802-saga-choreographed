package zen.labs.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;

public interface HandleInventoryEventUseCase {
    void handleOrderPlaced(String orderId, List<OrderedItem> items, BigDecimal amount, String shippingAddress);

    void handleRelease(String orderId);
}
