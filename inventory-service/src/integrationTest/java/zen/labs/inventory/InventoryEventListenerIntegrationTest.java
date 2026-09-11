package zen.labs.inventory;

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
import zen.labs.inventory.adapter.outbound.persistence.repository.InventoryReservationRepository;
import zen.labs.inventory.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.inventory.common.persistence.AbstractPostgresIntegrationTest;
import zen.labs.inventory.domain.ReservationStatus;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"spring.kafka.listener.auto-startup=true"})
@Testcontainers
class InventoryEventListenerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${application.saga.topics.order-events}")
    private String orderEventsTopic;

    @Value("${application.saga.topics.payment-events}")
    private String paymentEventsTopic;

    @Value("${application.saga.topics.inventory-events}")
    private String inventoryEventsTopic;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    @DisplayName("ORDER_PLACED event reserves inventory and writes INVENTORY_RESERVED outbox row")
    void orderPlaced_reservesInventory() {
        var orderId = "order-" + UUID.randomUUID();

        kafkaTemplate.send(orderEventsTopic, orderId, orderPlacedJson(orderId, "product-1", 5));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RESERVED");
            assertThat(rows.get(0).getAggregateType()).isEqualTo(inventoryEventsTopic);
        });

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var reservation = reservationRepository.findByOrderId(orderId);
            assertThat(reservation).isPresent();
            assertThat(reservation.get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        });
    }

    @Test
    @DisplayName("ORDER_PLACED event with qty > 100 writes INVENTORY_RESERVATION_FAILED outbox row")
    void orderPlaced_excessQty_reservationFailed() {
        var orderId = "order-" + UUID.randomUUID();

        kafkaTemplate.send(orderEventsTopic, orderId, orderPlacedJson(orderId, "product-1", 101));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RESERVATION_FAILED");
        });
    }

    @Test
    @DisplayName("ORDER_CANCELLATION_REQUESTED event releases inventory and writes INVENTORY_RELEASED outbox row")
    void orderCancellationRequested_releasesInventory() {
        var orderId = "order-" + UUID.randomUUID();
        kafkaTemplate.send(orderEventsTopic, orderId, orderPlacedJson(orderId, "product-1", 5));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(reservationRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(
                orderEventsTopic, orderId, sagaEventJson(SagaEventType.ORDER_CANCELLATION_REQUESTED, orderId));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RELEASED");
        });

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var reservation = reservationRepository.findByOrderId(orderId);
            assertThat(reservation).isPresent();
            assertThat(reservation.get().getStatus()).isEqualTo(ReservationStatus.RELEASED);
        });
    }

    @Test
    @DisplayName("PAYMENT_FAILED event releases inventory and writes INVENTORY_RELEASED outbox row")
    void paymentFailed_releasesInventory() {
        var orderId = "order-" + UUID.randomUUID();
        kafkaTemplate.send(orderEventsTopic, orderId, orderPlacedJson(orderId, "product-1", 5));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(reservationRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(paymentEventsTopic, orderId, sagaEventJson(SagaEventType.PAYMENT_FAILED, orderId));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RELEASED");
        });
    }

    @Test
    @DisplayName("PAYMENT_REFUNDED event releases inventory and writes INVENTORY_RELEASED outbox row")
    void paymentRefunded_releasesInventory() {
        var orderId = "order-" + UUID.randomUUID();
        kafkaTemplate.send(orderEventsTopic, orderId, orderPlacedJson(orderId, "product-1", 5));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(reservationRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(paymentEventsTopic, orderId, sagaEventJson(SagaEventType.PAYMENT_REFUNDED, orderId));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RELEASED");
        });
    }

    private String orderPlacedJson(String orderId, String productId, int quantity) {
        return """
                {"eventId":"%s","type":"ORDER_PLACED","orderId":"%s","failureReason":null,\
                "items":[{"productId":"%s","quantity":%d}],"amount":500.00,"shippingAddress":"123 Main St"}
                """
                .formatted(UUID.randomUUID(), orderId, productId, quantity)
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
