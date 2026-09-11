package zen.labs.order;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static zen.labs.infrastructure.profiles.SpringProfiles.INTEGRATION_TEST;
import static zen.labs.infrastructure.profiles.SpringProfiles.TEST;

import java.math.BigDecimal;
import java.util.List;
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
import zen.labs.order.adapter.outbound.persistence.repository.OrderRepository;
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;
import zen.labs.order.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"spring.kafka.listener.auto-startup=true"})
@Testcontainers
class SagaEventListenerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${application.saga.topics.inventory-events}")
    private String inventoryEventsTopic;

    @Value("${application.saga.topics.shipping-events}")
    private String shippingEventsTopic;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("SHIPMENT_CREATED event confirms a CONFIRMING order")
    void shipmentCreated_confirmsOrder() {
        var order = placeOrder();

        kafkaTemplate.send(
                shippingEventsTopic,
                order.getResourceId(),
                sagaEventJson(SagaEventType.SHIPMENT_CREATED, order.getResourceId()));

        await().atMost(10, SECONDS).untilAsserted(() -> assertStatus(order.getResourceId(), OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("INVENTORY_RESERVATION_FAILED event cancels a CONFIRMING order")
    void inventoryReservationFailed_cancelsOrder() {
        var order = placeOrder();

        kafkaTemplate.send(
                inventoryEventsTopic,
                order.getResourceId(),
                sagaEventJson(SagaEventType.INVENTORY_RESERVATION_FAILED, order.getResourceId()));

        await().atMost(10, SECONDS).untilAsserted(() -> assertStatus(order.getResourceId(), OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("INVENTORY_RELEASED event cancels a CANCELLING order")
    void inventoryReleased_cancelsOrder() {
        var order = placeOrder();
        cancelOrderUseCase.cancelOrder(order.getResourceId());

        kafkaTemplate.send(
                inventoryEventsTopic,
                order.getResourceId(),
                sagaEventJson(SagaEventType.INVENTORY_RELEASED, order.getResourceId()));

        await().atMost(10, SECONDS).untilAsserted(() -> assertStatus(order.getResourceId(), OrderStatus.CANCELLED));
    }

    private void assertStatus(String orderId, OrderStatus expected) {
        var entity = orderRepository.findByResourceId(orderId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(expected);
    }

    private Order placeOrder() {
        return placeOrderUseCase.placeOrder(new PlaceOrderCommand(
                UUID.randomUUID().toString(),
                "customer-1",
                List.of(new PlaceOrderCommand.Item("product-1", 2, BigDecimal.valueOf(25))),
                "123 Main St, Springfield, IL 62701"));
    }

    private String sagaEventJson(SagaEventType type, String orderId) {
        return """
                {"eventId":"%s","type":"%s","orderId":"%s","failureReason":null,"items":null,"amount":null,"shippingAddress":null}
                """
                .formatted(UUID.randomUUID(), type.name(), orderId)
                .strip();
    }
}
