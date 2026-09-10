package zen.labs.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import zen.labs.adapters.common.kafka.SagaEventItem;

public interface HandleInventoryEventUseCase {
    void handleOrderPlaced(String orderId, List<SagaEventItem> items, BigDecimal amount, String shippingAddress);

    void handleRelease(String orderId);
}
