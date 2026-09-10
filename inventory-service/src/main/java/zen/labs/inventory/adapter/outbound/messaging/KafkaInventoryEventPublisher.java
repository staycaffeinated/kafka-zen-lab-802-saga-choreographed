package zen.labs.inventory.adapter.outbound.messaging;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;

@Component
@Slf4j
public class KafkaInventoryEventPublisher implements InventoryEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String inventoryEventsTopic;

    public KafkaInventoryEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.inventory-events}") String inventoryEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.inventoryEventsTopic = inventoryEventsTopic;
    }

    @Override
    public void publishInventoryReserved(String orderId, BigDecimal amount, String shippingAddress) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(),
                        SagaEventType.INVENTORY_RESERVED,
                        orderId,
                        null,
                        null,
                        amount,
                        shippingAddress));
    }

    @Override
    public void publishInventoryReservationFailed(String orderId, String reason) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(),
                        SagaEventType.INVENTORY_RESERVATION_FAILED,
                        orderId,
                        reason,
                        null,
                        null,
                        null));
    }

    @Override
    public void publishInventoryReleased(String orderId) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(),
                        SagaEventType.INVENTORY_RELEASED,
                        orderId,
                        null,
                        null,
                        null,
                        null));
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate
                    .send(inventoryEventsTopic, key, jsonMapper.writeValueAsString(event))
                    .get();
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish inventory event: " + event.type(), e);
        }
    }
}
