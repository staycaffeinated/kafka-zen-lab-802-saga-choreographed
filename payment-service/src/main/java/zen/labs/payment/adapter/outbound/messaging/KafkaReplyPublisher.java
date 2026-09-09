/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.outbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.payment.application.port.outbound.SagaReplyPublisher;

@Component
@Slf4j
public class KafkaReplyPublisher implements SagaReplyPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String orchestratorReplies;

    public KafkaReplyPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.orchestrator-replies}") String orchestratorReplies) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.orchestratorReplies = orchestratorReplies;
    }

    @Override
    public void publish(String replyType, String sagaId, String orderId, boolean success, String failureReason) {
        try {
            var reply = new SagaReply(sagaId, orderId, replyType, success, failureReason);
            var payload = jsonMapper.writeValueAsString(reply);
            kafkaTemplate.send(orchestratorReplies, orderId, payload);
            log.debug("Published {} reply: sagaId={}", replyType, sagaId);
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish saga reply: " + replyType, e);
        }
    }
}
