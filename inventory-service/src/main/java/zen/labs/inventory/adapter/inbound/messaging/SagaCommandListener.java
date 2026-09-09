/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaCommandType;
import zen.labs.inventory.application.port.inbound.HandleInventoryCommandUseCase;

@Component
@Slf4j
@SuppressWarnings({
    "java:S1301" // prefer switch over if-else
    ,
    "java:S131" // don't need default block in switch"
})
public class SagaCommandListener {

    private final HandleInventoryCommandUseCase handleInventoryCommandUseCase;
    private final JsonMapper jsonMapper;

    public SagaCommandListener(HandleInventoryCommandUseCase handleInventoryCommandUseCase, JsonMapper jsonMapper) {
        this.handleInventoryCommandUseCase = handleInventoryCommandUseCase;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.commands}")
    public void onCommand(@Payload String payload, Acknowledgment ack) {
        try {
            var cmd = jsonMapper.readValue(payload, SagaCommand.class);
            MDC.put("sagaId", cmd.sagaId());
            MDC.put("orderId", cmd.orderId());
            log.debug("Received saga command: type={} sagaId={}", cmd.commandType(), cmd.sagaId());
            dispatch(cmd);
        } catch (Exception e) {
            log.error("Failed to process saga command: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }

    private void dispatch(SagaCommand cmd) {
        SagaCommandType type;
        try {
            type = SagaCommandType.valueOf(cmd.commandType());
        } catch (IllegalArgumentException _) {
            log.warn("Received an unknown inventory command type. The unrecognized type is: {}", cmd.commandType());
            return;
        }
        switch (type) {
            case RESERVE_INVENTORY -> {
                var items = cmd.items().stream()
                        .map(i -> new HandleInventoryCommandUseCase.Item(i.productId(), i.quantity()))
                        .toList();
                handleInventoryCommandUseCase.reserveInventory(cmd.sagaId(), cmd.orderId(), items);
            }
            case RELEASE_INVENTORY -> handleInventoryCommandUseCase.releaseInventory(cmd.sagaId(), cmd.orderId());
        }
    }
}
