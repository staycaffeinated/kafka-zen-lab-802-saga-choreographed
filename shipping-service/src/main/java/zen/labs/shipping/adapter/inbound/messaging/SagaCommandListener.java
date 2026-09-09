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
import zen.labs.adapters.common.kafka.SagaCommandType;
import zen.labs.shipping.application.port.inbound.HandleShipmentCommandUseCase;

@Component
@Slf4j
public class SagaCommandListener {

    private final HandleShipmentCommandUseCase handleShipmentCommandUseCase;
    private final JsonMapper jsonMapper;

    public SagaCommandListener(HandleShipmentCommandUseCase handleShipmentCommandUseCase, JsonMapper jsonMapper) {
        this.handleShipmentCommandUseCase = handleShipmentCommandUseCase;
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
            log.warn("Unknown shipment command type: {}", cmd.commandType());
            return;
        }
        if (type == SagaCommandType.CREATE_SHIPMENT) {
            handleShipmentCommandUseCase.createShipment(cmd.sagaId(), cmd.orderId(), cmd.shippingAddress());
        }
    }
}
