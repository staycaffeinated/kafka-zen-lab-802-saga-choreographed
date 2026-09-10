package zen.labs.order.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;

@Component
@Slf4j
public class SagaEventListener {

    private final HandleSagaEventUseCase eventHandler;
    private final JsonMapper jsonMapper;

    public SagaEventListener(HandleSagaEventUseCase eventHandler, JsonMapper jsonMapper) {
        this.eventHandler = eventHandler;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = {
                "${application.saga.topics.inventory-events}",
                "${application.saga.topics.payment-events}",
                "${application.saga.topics.shipping-events}"
            })
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            MDC.put("eventType", event.type().name());
            log.debug("Received saga event: type={} orderId={}", event.type(), event.orderId());
            dispatch(event);
        } catch (Exception e) {
            log.error("Failed to process saga event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }

    private void dispatch(SagaEvent event) {
        switch (event.type()) {
            case INVENTORY_RESERVATION_FAILED -> eventHandler.onInventoryReservationFailed(event.orderId());
            case INVENTORY_RELEASED -> eventHandler.onInventoryReleased(event.orderId());
            case SHIPMENT_CREATED -> eventHandler.onShipmentCreated(event.orderId());
            default -> log.debug("Ignoring event type: {}", event.type());
        }
    }
}
