package zen.labs.inventory.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.inventory.application.port.inbound.HandleInventoryEventUseCase;

@Component
@Slf4j
public class PaymentEventListener {

    private final HandleInventoryEventUseCase eventUseCase;
    private final JsonMapper jsonMapper;

    public PaymentEventListener(HandleInventoryEventUseCase eventUseCase, JsonMapper jsonMapper) {
        this.eventUseCase = eventUseCase;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${application.saga.topics.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}-payment")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            switch (event) {
                case SagaEvent.PaymentFailedEvent e -> eventUseCase.handleRelease(e.orderId());
                case SagaEvent.PaymentRefundedEvent e -> eventUseCase.handleRelease(e.orderId());
                default ->
                    log.debug("Ignoring payment event: {}", event.getClass().getSimpleName());
            }
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
