/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.outbound.messaging;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;

@Component
@Slf4j
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String paymentEventsTopic;

    public KafkaPaymentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.payment-events}") String paymentEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    @Override
    public void publishPaymentProcessed(String orderId, String shippingAddress) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(),
                        SagaEventType.PAYMENT_PROCESSED,
                        orderId,
                        null,
                        null,
                        null,
                        shippingAddress));
    }

    @Override
    public void publishPaymentFailed(String orderId, String reason) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(), SagaEventType.PAYMENT_FAILED, orderId, reason, null, null, null));
    }

    @Override
    public void publishPaymentRefunded(String orderId) {
        publish(
                orderId,
                new SagaEvent(
                        UUID.randomUUID().toString(), SagaEventType.PAYMENT_REFUNDED, orderId, null, null, null, null));
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate
                    .send(paymentEventsTopic, key, jsonMapper.writeValueAsString(event))
                    .get();
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish payment event: " + event.type(), e);
        }
    }
}
