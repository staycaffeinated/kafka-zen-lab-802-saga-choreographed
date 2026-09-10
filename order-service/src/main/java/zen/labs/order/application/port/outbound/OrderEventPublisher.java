package zen.labs.order.application.port.outbound;

import java.math.BigDecimal;
import java.util.List;
import zen.labs.order.domain.OrderItem;

public interface OrderEventPublisher {
    void publishOrderPlaced(String orderId, List<OrderItem> items, BigDecimal amount, String shippingAddress);

    void publishCancellationRequested(String orderId);
}
