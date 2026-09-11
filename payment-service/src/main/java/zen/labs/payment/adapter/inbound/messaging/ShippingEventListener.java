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
public class ShippingEventListener {

    private final HandlePaymentEventUseCase eventUseCase;
    private final JsonMapper jsonMapper;

    public ShippingEventListener(HandlePaymentEventUseCase eventUseCase, JsonMapper jsonMapper) {
        this.eventUseCase = eventUseCase;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${application.saga.topics.shipping-events}",
            groupId = "${spring.kafka.consumer.group-id}-shipping")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        log.info("===> Received ShippingEvent...");
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == SagaEventType.SHIPMENT_FAILED) {
                log.info("===> Shipment failed; orderId: {}", event.orderId());
                eventUseCase.handleShipmentFailed(event.orderId());
            } else {
                log.debug("Ignoring shipping event: {}", event.type());
            }
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
