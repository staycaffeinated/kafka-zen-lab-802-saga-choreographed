package zen.labs.shipping;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static zen.labs.infrastructure.profiles.SpringProfiles.INTEGRATION_TEST;
import static zen.labs.infrastructure.profiles.SpringProfiles.TEST;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.shipping.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.shipping.adapter.outbound.persistence.repository.ShipmentRepository;
import zen.labs.shipping.domain.ShipmentStatus;
import zen.labs.shipping.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"spring.kafka.listener.auto-startup=true"})
@Testcontainers
class ShippingEventListenerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${application.saga.topics.payment-events}")
    private String paymentEventsTopic;

    @Value("${application.saga.topics.order-events}")
    private String orderEventsTopic;

    @Value("${application.saga.topics.shipping-events}")
    private String shippingEventsTopic;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        shipmentRepository.deleteAll();
    }

    @Test
    @DisplayName("PAYMENT_PROCESSED event creates shipment and writes SHIPMENT_CREATED outbox row")
    void paymentProcessed_createsShipment() {
        var orderId = "order-" + UUID.randomUUID();
        var address = "123 Main St, Springfield, IL 62701";

        kafkaTemplate.send(paymentEventsTopic, orderId, paymentProcessedJson(orderId, address));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("SHIPMENT_CREATED");
            assertThat(rows.get(0).getAggregateType()).isEqualTo(shippingEventsTopic);
        });

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var shipment = shipmentRepository.findByOrderId(orderId);
            assertThat(shipment).isPresent();
            assertThat(shipment.get().getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(shipment.get().getShippingAddress()).isEqualTo(address);
        });
    }

    @Test
    @DisplayName("PAYMENT_PROCESSED event with blank address writes SHIPMENT_FAILED outbox row")
    void paymentProcessed_blankAddress_shipmentFailed() {
        var orderId = "order-" + UUID.randomUUID();

        kafkaTemplate.send(paymentEventsTopic, orderId, paymentProcessedJson(orderId, ""));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("SHIPMENT_FAILED");
        });
    }

    @Test
    @DisplayName("duplicate PAYMENT_PROCESSED event is idempotent — one shipment, no new outbox row")
    void paymentProcessed_duplicate_isIdempotent() {
        var orderId = "order-" + UUID.randomUUID();
        var address = "123 Main St, Springfield, IL 62701";

        kafkaTemplate.send(paymentEventsTopic, orderId, paymentProcessedJson(orderId, address));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(shipmentRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(paymentEventsTopic, orderId, paymentProcessedJson(orderId, address));

        await().during(2, SECONDS).atMost(4, SECONDS).until(() -> outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList()
                .isEmpty());
        assertThat(shipmentRepository.findAll().stream()
                        .filter(s -> s.getOrderId().equals(orderId))
                        .toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("ORDER_CANCELLATION_REQUESTED event cancels shipment silently — no outbox row")
    void orderCancellationRequested_cancelsShipmentSilently() {
        var orderId = "order-" + UUID.randomUUID();
        kafkaTemplate.send(paymentEventsTopic, orderId, paymentProcessedJson(orderId, "123 Main St"));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(shipmentRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(
                orderEventsTopic, orderId, sagaEventJson(SagaEventType.ORDER_CANCELLATION_REQUESTED, orderId));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var shipment = shipmentRepository.findByOrderId(orderId);
            assertThat(shipment).isPresent();
            assertThat(shipment.get().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        });

        assertThat(outboxRepository.findAll().stream()
                        .filter(r -> r.getAggregateId().equals(orderId))
                        .toList())
                .isEmpty();
    }

    private String paymentProcessedJson(String orderId, String shippingAddress) {
        return """
                {"eventId":"%s","type":"PAYMENT_PROCESSED","orderId":"%s","failureReason":null,\
                "items":null,"amount":null,"shippingAddress":"%s"}
                """
                .formatted(UUID.randomUUID(), orderId, shippingAddress)
                .strip();
    }

    private String sagaEventJson(SagaEventType type, String orderId) {
        return """
                {"eventId":"%s","type":"%s","orderId":"%s","failureReason":null,"items":null,"amount":null,"shippingAddress":null}
                """
                .formatted(UUID.randomUUID(), type.name(), orderId)
                .strip();
    }
}
