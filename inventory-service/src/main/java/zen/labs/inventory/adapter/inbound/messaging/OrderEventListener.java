package zen.labs.inventory.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.inventory.application.port.inbound.HandleInventoryEventUseCase;
import zen.labs.inventory.application.port.inbound.OrderedItem;

@Component
@Slf4j
public class OrderEventListener {

    private final HandleInventoryEventUseCase eventUseCase;
    private final JsonMapper jsonMapper;

    public OrderEventListener(HandleInventoryEventUseCase eventUseCase, JsonMapper jsonMapper) {
        this.eventUseCase = eventUseCase;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${application.saga.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}-order")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            switch (event.type()) {
                case ORDER_PLACED -> {
                    var items = event.items().stream()
                            .map(i -> new OrderedItem(i.productId(), i.quantity()))
                            .toList();
                    eventUseCase.handleOrderPlaced(
                            event.orderId(), items, event.amount(), event.shippingAddress());
                }
                case ORDER_CANCELLATION_REQUESTED -> eventUseCase.handleRelease(event.orderId());
                default -> log.debug("Ignoring order event: {}", event.type());
            }
        } catch (Exception e) {
            log.error("Failed to process order event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
