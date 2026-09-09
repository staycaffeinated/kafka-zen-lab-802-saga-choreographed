/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaReplyType;
import zen.labs.order.application.port.inbound.HandleSagaReplyUseCase;

@Component
@Slf4j
public class SagaReplyListener {

    private final HandleSagaReplyUseCase handleSagaReplyUseCase;
    private final JsonMapper jsonMapper;

    public SagaReplyListener(HandleSagaReplyUseCase handleSagaReplyUseCase, JsonMapper jsonMapper) {
        this.handleSagaReplyUseCase = handleSagaReplyUseCase;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.orchestrator-replies}")
    public void onReply(@Payload String payload, Acknowledgment ack) {
        try {
            var reply = jsonMapper.readValue(payload, SagaReply.class);
            MDC.put("sagaId", reply.sagaId());
            MDC.put("orderId", reply.orderId());
            log.debug(
                    "Received saga reply: type={} sagaId={} success={}",
                    reply.replyType(),
                    reply.sagaId(),
                    reply.success());
            dispatch(reply);
        } catch (Exception e) {
            log.error("Failed to process saga reply: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }

    private void dispatch(SagaReply reply) {
        SagaReplyType type;
        try {
            type = SagaReplyType.valueOf(reply.replyType());
        } catch (IllegalArgumentException _) {
            log.warn("Unknown saga reply type: {}", reply.replyType());
            return;
        }
        switch (type) {
            case INVENTORY_RESERVED, INVENTORY_RESERVATION_FAILED ->
                handleSagaReplyUseCase.onInventoryReply(
                        reply.sagaId(), reply.orderId(), reply.success(), reply.failureReason());
            case INVENTORY_RELEASED -> handleSagaReplyUseCase.onInventoryReleasedReply(reply.sagaId(), reply.orderId());
            case PAYMENT_PROCESSED, PAYMENT_FAILED ->
                handleSagaReplyUseCase.onPaymentReply(
                        reply.sagaId(), reply.orderId(), reply.success(), reply.failureReason());
            case PAYMENT_REFUNDED -> handleSagaReplyUseCase.onPaymentRefundedReply(reply.sagaId(), reply.orderId());
            case SHIPMENT_CREATED, SHIPMENT_FAILED ->
                handleSagaReplyUseCase.onShipmentReply(
                        reply.sagaId(), reply.orderId(), reply.success(), reply.failureReason());
        }
    }
}
