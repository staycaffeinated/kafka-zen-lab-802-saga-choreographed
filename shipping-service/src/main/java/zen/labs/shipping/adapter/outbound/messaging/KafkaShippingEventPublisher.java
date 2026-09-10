/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.outbound.messaging;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;

@Component
@Slf4j
public class KafkaShippingEventPublisher implements ShippingEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String shippingEventsTopic;

    public KafkaShippingEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.shipping-events}") String shippingEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.shippingEventsTopic = shippingEventsTopic;
    }

    @Override
    public void publishShipmentCreated(String orderId) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(), SagaEventType.SHIPMENT_CREATED, orderId, null, null, null, null));
    }

    @Override
    public void publishShipmentFailed(String orderId, String reason) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(),
                        SagaEventType.SHIPMENT_FAILED,
                        orderId,
                        reason,
                        null,
                        null,
                        null));
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate
                    .send(shippingEventsTopic, key, jsonMapper.writeValueAsString(event))
                    .get();
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish shipping event: " + event.type(), e);
        }
    }
}
