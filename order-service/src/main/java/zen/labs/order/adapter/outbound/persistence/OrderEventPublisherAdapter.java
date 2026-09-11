package zen.labs.order.adapter.outbound.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventItem;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.order.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.OrderItem;

@Component
@Slf4j
public class OrderEventPublisherAdapter implements OrderEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final String orderEventsTopic;

    public OrderEventPublisherAdapter(
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.order-events}") String orderEventsTopic) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.orderEventsTopic = orderEventsTopic;
    }

    @Override
    public void publishOrderPlaced(String orderId, List<OrderItem> items, BigDecimal amount, String shippingAddress) {
        var event = new SagaEvent(
                UUID.randomUUID().toString(),
                SagaEventType.ORDER_PLACED,
                orderId,
                null,
                items.stream()
                        .map(i -> new SagaEventItem(i.productId(), i.quantity()))
                        .toList(),
                amount,
                shippingAddress);
        persist(orderId, SagaEventType.ORDER_PLACED, event);
    }

    @Override
    public void publishCancellationRequested(String orderId) {
        var event = new SagaEvent(
                UUID.randomUUID().toString(),
                SagaEventType.ORDER_CANCELLATION_REQUESTED,
                orderId,
                null,
                null,
                null,
                null);
        persist(orderId, SagaEventType.ORDER_CANCELLATION_REQUESTED, event);
    }

    private void persist(String orderId, SagaEventType type, SagaEvent event) {
        try {
            outboxRepository.save(OutboxJpaEntity.builder()
                    .aggregateType(orderEventsTopic)
                    .aggregateId(orderId)
                    .type(type.name())
                    .payload(jsonMapper.writeValueAsString(event))
                    .build());
            log.debug("Outbox row persisted: type={} orderId={}", type, orderId);
        } catch (Exception e) {
            throw new ApplicationException("Failed to persist outbox event: " + type, e);
        }
    }
}
