package zen.labs.order;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.order.adapter.outbound.persistence.repository.OrderRepository;
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;
import zen.labs.order.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class SagaEventHandlerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    private HandleSagaEventUseCase sagaEventHandler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("onShipmentCreated transitions CONFIRMING order to CONFIRMED")
    void onShipmentCreated_confirmsOrder() {
        var order = placeOrder();

        sagaEventHandler.onShipmentCreated(order.getResourceId());

        assertStatus(order.getResourceId(), OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("onShipmentCreated does nothing when order is not CONFIRMING")
    void onShipmentCreated_doesNothingWhenNotConfirming() {
        var order = placeOrder();
        cancelOrderUseCase.cancelOrder(order.getResourceId());

        sagaEventHandler.onShipmentCreated(order.getResourceId());

        assertStatus(order.getResourceId(), OrderStatus.CANCELLING);
    }

    @Test
    @DisplayName("onInventoryReservationFailed transitions CONFIRMING order to CANCELLED")
    void onInventoryReservationFailed_cancelsOrder() {
        var order = placeOrder();

        sagaEventHandler.onInventoryReservationFailed(order.getResourceId());

        assertStatus(order.getResourceId(), OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("onInventoryReleased transitions CANCELLING order to CANCELLED")
    void onInventoryReleased_cancelsOrderWhenCancelling() {
        var order = placeOrder();
        cancelOrderUseCase.cancelOrder(order.getResourceId());

        sagaEventHandler.onInventoryReleased(order.getResourceId());

        assertStatus(order.getResourceId(), OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("onInventoryReleased does nothing when order is already CONFIRMED")
    void onInventoryReleased_doesNothingWhenConfirmed() {
        var order = placeOrder();
        sagaEventHandler.onShipmentCreated(order.getResourceId());

        sagaEventHandler.onInventoryReleased(order.getResourceId());

        assertStatus(order.getResourceId(), OrderStatus.CONFIRMED);
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
}
