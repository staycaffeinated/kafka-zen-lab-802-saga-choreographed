package zen.labs.payment;

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
import zen.labs.payment.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.payment.adapter.outbound.persistence.repository.PaymentRepository;
import zen.labs.payment.domain.PaymentStatus;
import zen.labs.payment.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"spring.kafka.listener.auto-startup=true"})
@Testcontainers
class PaymentEventListenerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${application.saga.topics.inventory-events}")
    private String inventoryEventsTopic;

    @Value("${application.saga.topics.order-events}")
    private String orderEventsTopic;

    @Value("${application.saga.topics.shipping-events}")
    private String shippingEventsTopic;

    @Value("${application.saga.topics.payment-events}")
    private String paymentEventsTopic;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("INVENTORY_RESERVED event processes payment and writes PAYMENT_PROCESSED outbox row")
    void inventoryReserved_processesPayment() {
        var orderId = "order-" + UUID.randomUUID();

        kafkaTemplate.send(inventoryEventsTopic, orderId, inventoryReservedJson(orderId, "500.00", "123 Main St"));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_PROCESSED");
            assertThat(rows.get(0).getAggregateType()).isEqualTo(paymentEventsTopic);
        });

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var payment = paymentRepository.findByOrderId(orderId);
            assertThat(payment).isPresent();
            assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
        });
    }

    @Test
    @DisplayName("INVENTORY_RESERVED event with amount over 10000 writes PAYMENT_FAILED outbox row")
    void inventoryReserved_excessAmount_paymentFailed() {
        var orderId = "order-" + UUID.randomUUID();

        kafkaTemplate.send(inventoryEventsTopic, orderId, inventoryReservedJson(orderId, "10001.00", "123 Main St"));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_FAILED");
        });
    }

    @Test
    @DisplayName("SHIPMENT_FAILED event refunds payment and writes PAYMENT_REFUNDED outbox row")
    void shipmentFailed_refundsPayment() {
        var orderId = "order-" + UUID.randomUUID();
        kafkaTemplate.send(inventoryEventsTopic, orderId, inventoryReservedJson(orderId, "500.00", "123 Main St"));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(paymentRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(shippingEventsTopic, orderId, sagaEventJson(SagaEventType.SHIPMENT_FAILED, orderId));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var rows = outboxRepository.findAll().stream()
                    .filter(r -> r.getAggregateId().equals(orderId))
                    .toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_REFUNDED");
        });

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var payment = paymentRepository.findByOrderId(orderId);
            assertThat(payment).isPresent();
            assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });
    }

    @Test
    @DisplayName("ORDER_CANCELLATION_REQUESTED event refunds payment silently — no outbox row")
    void orderCancellationRequested_refundsPaymentSilently() {
        var orderId = "order-" + UUID.randomUUID();
        kafkaTemplate.send(inventoryEventsTopic, orderId, inventoryReservedJson(orderId, "500.00", "123 Main St"));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(paymentRepository.findByOrderId(orderId))
                .isPresent());
        outboxRepository.deleteAll();

        kafkaTemplate.send(
                orderEventsTopic, orderId, sagaEventJson(SagaEventType.ORDER_CANCELLATION_REQUESTED, orderId));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var payment = paymentRepository.findByOrderId(orderId);
            assertThat(payment).isPresent();
            assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });

        assertThat(outboxRepository.findAll().stream()
                        .filter(r -> r.getAggregateId().equals(orderId))
                        .toList())
                .isEmpty();
    }

    private String inventoryReservedJson(String orderId, String amount, String shippingAddress) {
        return """
                {"eventId":"%s","type":"INVENTORY_RESERVED","orderId":"%s","failureReason":null,\
                "items":null,"amount":%s,"shippingAddress":"%s"}
                """
                .formatted(UUID.randomUUID(), orderId, amount, shippingAddress)
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
