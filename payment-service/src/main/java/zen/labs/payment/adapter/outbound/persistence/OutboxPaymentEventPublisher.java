package zen.labs.payment.adapter.outbound.persistence;

import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.payment.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.payment.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;

@Component
@Slf4j
public class OutboxPaymentEventPublisher implements PaymentEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final String paymentEventsTopic;

    public OutboxPaymentEventPublisher(
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.payment-events}") String paymentEventsTopic) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    @Override
    public void publishPaymentProcessed(String orderId, String shippingAddress) {
        persist(
                orderId,
                SagaEventType.PAYMENT_PROCESSED,
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
        persist(
                orderId,
                SagaEventType.PAYMENT_FAILED,
                new SagaEvent(
                        UUID.randomUUID().toString(), SagaEventType.PAYMENT_FAILED, orderId, reason, null, null, null));
    }

    @Override
    public void publishPaymentRefunded(String orderId) {
        persist(
                orderId,
                SagaEventType.PAYMENT_REFUNDED,
                new SagaEvent(
                        UUID.randomUUID().toString(), SagaEventType.PAYMENT_REFUNDED, orderId, null, null, null, null));
    }

    private void persist(String orderId, SagaEventType type, SagaEvent event) {
        try {
            outboxRepository.save(OutboxJpaEntity.builder()
                    .aggregateType(paymentEventsTopic)
                    .aggregateId(orderId)
                    .type(type.name())
                    .payload(jsonMapper.writeValueAsString(event))
                    .createdAt(Instant.now())
                    .build());
            log.debug("Outbox row persisted: type={} orderId={}", type, orderId);
        } catch (Exception e) {
            throw new ApplicationException("Failed to persist outbox event: " + type, e);
        }
    }
}
