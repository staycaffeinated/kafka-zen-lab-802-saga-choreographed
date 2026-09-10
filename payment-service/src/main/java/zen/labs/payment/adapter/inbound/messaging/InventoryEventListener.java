/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.payment.application.port.inbound.HandlePaymentEventUseCase;

@Component
@Slf4j
public class InventoryEventListener {

    private final HandlePaymentEventUseCase eventUseCase;
    private final JsonMapper jsonMapper;

    public InventoryEventListener(HandlePaymentEventUseCase eventUseCase, JsonMapper jsonMapper) {
        this.eventUseCase = eventUseCase;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${application.saga.topics.inventory-events}",
            groupId = "${spring.kafka.consumer.group-id}-inventory")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == SagaEventType.INVENTORY_RESERVED) {
                eventUseCase.handleInventoryReserved(event.orderId(), event.amount(), event.shippingAddress());
            } else {
                log.debug("Ignoring inventory event: {}", event.type());
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
