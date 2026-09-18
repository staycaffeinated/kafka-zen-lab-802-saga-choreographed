package zen.labs.inventory.adapter.outbound.persistence;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.inventory.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.inventory.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;

@Component
@Slf4j
public class InventoryEventPublisherAdapter implements InventoryEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final String inventoryEventsTopic;

    public InventoryEventPublisherAdapter(
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.inventory-events}") String inventoryEventsTopic) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.inventoryEventsTopic = inventoryEventsTopic;
    }

    @Override
    public void publishInventoryReserved(String orderId, BigDecimal amount, String shippingAddress) {
        persist(
                orderId,
                SagaEventType.INVENTORY_RESERVED,
                new SagaEvent.InventoryReservedEvent(UUID.randomUUID().toString(), orderId, amount, shippingAddress));
    }

    @Override
    public void publishInventoryReservationFailed(String orderId, String reason) {
        persist(
                orderId,
                SagaEventType.INVENTORY_RESERVATION_FAILED,
                new SagaEvent.InventoryReservationFailedEvent(UUID.randomUUID().toString(), orderId, reason));
    }

    @Override
    public void publishInventoryReleased(String orderId) {
        persist(
                orderId,
                SagaEventType.INVENTORY_RELEASED,
                new SagaEvent.InventoryReleasedEvent(UUID.randomUUID().toString(), orderId));
    }

    private void persist(String orderId, SagaEventType type, SagaEvent event) {
        try {
            outboxRepository.save(OutboxJpaEntity.builder()
                    .aggregateType(inventoryEventsTopic)
                    .aggregateId(orderId)
                    .type(type.name())
                    .payload(jsonMapper.writeValueAsString(event))
                    .build());
            log.debug("===> Outbox row persisted: type={} orderId={}", type, orderId);
        } catch (Exception e) {
            throw new ApplicationException("Failed to persist outbox event: " + type, e);
        }
    }
}
