package zen.labs.adapters.common.kafka;

import java.math.BigDecimal;
import java.util.List;

public record SagaEvent(
        String eventId,
        SagaEventType type,
        String orderId,
        String failureReason,
        List<SagaEventItem> items,
        BigDecimal amount,
        String shippingAddress) {}
