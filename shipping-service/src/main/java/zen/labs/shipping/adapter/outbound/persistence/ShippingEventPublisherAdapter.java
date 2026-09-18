/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.outbound.persistence;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.shipping.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.shipping.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;

@Component
@Slf4j
public class ShippingEventPublisherAdapter implements ShippingEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final String shippingEventsTopic;

    public ShippingEventPublisherAdapter(
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.shipping-events}") String shippingEventsTopic) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.shippingEventsTopic = shippingEventsTopic;
    }

    @Override
    public void publishShipmentCreated(String orderId) {
        log.info("===> Persisting shipment created record for order: {}", orderId);
        persist(
                orderId,
                SagaEventType.SHIPMENT_CREATED,
                new SagaEvent.ShipmentCreatedEvent(UUID.randomUUID().toString(), orderId));
    }

    @Override
    public void publishShipmentFailed(String orderId, String reason) {
        log.info("===> Persisting shipment failed record for order: {}", orderId);
        persist(
                orderId,
                SagaEventType.SHIPMENT_FAILED,
                new SagaEvent.ShipmentFailedEvent(UUID.randomUUID().toString(), orderId, reason));
    }

    private void persist(String orderId, SagaEventType type, SagaEvent event) {
        try {
            outboxRepository.save(OutboxJpaEntity.builder()
                    .aggregateType(shippingEventsTopic)
                    .aggregateId(orderId)
                    .type(type.name())
                    .payload(jsonMapper.writeValueAsString(event))
                    .build());
            log.debug("===> Shipping Outbox row persisted: type={} orderId={}", type, orderId);
        } catch (Exception e) {
            throw new ApplicationException("Failed to persist outbox event: " + type, e);
        }
    }
}
