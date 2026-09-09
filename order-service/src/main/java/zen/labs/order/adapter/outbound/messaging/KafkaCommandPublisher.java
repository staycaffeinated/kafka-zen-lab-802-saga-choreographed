/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.messaging;

import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaCommandType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.order.application.port.outbound.SagaCommandPublisher;
import zen.labs.order.domain.OrderItem;

@Component
@Slf4j
public class KafkaCommandPublisher implements SagaCommandPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String paymentCommands;
    private final String inventoryCommands;
    private final String shippingCommands;

    public KafkaCommandPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.payment-commands}") String paymentCommands,
            @Value("${application.saga.topics.inventory-commands}") String inventoryCommands,
            @Value("${application.saga.topics.shipping-commands}") String shippingCommands) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.paymentCommands = paymentCommands;
        this.inventoryCommands = inventoryCommands;
        this.shippingCommands = shippingCommands;
    }

    @Override
    public void reserveInventory(String sagaId, String orderId, List<OrderItem> items) {
        var cmd = new SagaCommand(
                sagaId,
                orderId,
                SagaCommandType.RESERVE_INVENTORY.name(),
                items.stream()
                        .map(i -> new SagaCommandItem(i.productId(), i.quantity()))
                        .toList(),
                null,
                null);
        publish(inventoryCommands, orderId, cmd);
    }

    @Override
    public void processPayment(String sagaId, String orderId, BigDecimal amount) {
        var cmd = new SagaCommand(sagaId, orderId, SagaCommandType.PROCESS_PAYMENT.name(), null, amount, null);
        publish(paymentCommands, orderId, cmd);
    }

    @Override
    public void createShipment(String sagaId, String orderId, String shippingAddress) {
        var cmd = new SagaCommand(sagaId, orderId, SagaCommandType.CREATE_SHIPMENT.name(), null, null, shippingAddress);
        publish(shippingCommands, orderId, cmd);
    }

    @Override
    public void releaseInventory(String sagaId, String orderId) {
        var cmd = new SagaCommand(sagaId, orderId, SagaCommandType.RELEASE_INVENTORY.name(), null, null, null);
        publish(inventoryCommands, orderId, cmd);
    }

    @Override
    public void refundPayment(String sagaId, String orderId) {
        var cmd = new SagaCommand(sagaId, orderId, SagaCommandType.REFUND_PAYMENT.name(), null, null, null);
        publish(paymentCommands, orderId, cmd);
    }

    private void publish(String topic, String key, SagaCommand cmd) {
        try {
            var payload = jsonMapper.writeValueAsString(cmd);
            kafkaTemplate.send(topic, key, payload);
            log.debug("Published {} to topic={}", cmd.commandType(), topic);
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish command: " + cmd.commandType(), e);
        }
    }
}
