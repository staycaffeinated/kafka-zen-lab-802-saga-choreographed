package zen.labs.order.adapter.outbound.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventItem;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.OrderItem;

@Component
@Slf4j
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String orderEventsTopic;

    public KafkaOrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.order-events}") String orderEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.orderEventsTopic = orderEventsTopic;
    }

    @Override
    public void publishOrderPlaced(String orderId, List<OrderItem> items, BigDecimal amount, String shippingAddress) {
        var event = new SagaEvent(
                UUID.randomUUID().toString(),
                SagaEventType.ORDER_PLACED,
                orderId,
                null,
                items.stream()
                        .map(i -> new SagaEventItem(i.productId(), i.quantity()))
                        .toList(),
                amount,
                shippingAddress);
        publish(orderId, event);
    }

    @Override
    public void publishCancellationRequested(String orderId) {
        var event = new SagaEvent(
                UUID.randomUUID().toString(),
                SagaEventType.ORDER_CANCELLATION_REQUESTED,
                orderId,
                null,
                null,
                null,
                null);
        publish(orderId, event);
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate
                    .send(orderEventsTopic, key, jsonMapper.writeValueAsString(event))
                    .get();
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish event: " + event.type(), e);
        }
    }
}
