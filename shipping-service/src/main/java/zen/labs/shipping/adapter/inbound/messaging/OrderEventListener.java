/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.shipping.application.port.inbound.HandleShipmentEventUseCase;

@Component
@Slf4j
public class OrderEventListener {

    private final HandleShipmentEventUseCase eventUseCase;
    private final JsonMapper jsonMapper;

    public OrderEventListener(HandleShipmentEventUseCase eventUseCase, JsonMapper jsonMapper) {
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
            switch (event) {
                case SagaEvent.OrderCancellationRequestedEvent e ->
                    eventUseCase.handleOrderCancellationRequested(e.orderId());
                default ->
                    log.debug("Ignoring order event: {}", event.getClass().getSimpleName());
            }
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
