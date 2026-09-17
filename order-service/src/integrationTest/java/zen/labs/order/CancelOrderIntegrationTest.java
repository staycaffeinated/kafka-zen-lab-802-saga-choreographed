package zen.labs.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.domain.Order;
import zen.labs.order.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class CancelOrderIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    private HandleSagaEventUseCase sagaEventHandler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Value("${application.saga.topics.order-events}")
    private String orderEventsTopic;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("cancelOrder writes ORDER_CANCELLATION_REQUESTED outbox row with correct routing fields")
    void cancelOrder_writesOutboxRow() {
        var order = placeOrder();
        outboxRepository.deleteAll();

        cancelOrderUseCase.cancelOrder(order.getResourceId());

        var rows = outboxRepository.findAll().stream()
                .filter(r -> order.getResourceId().equals(r.getAggregateId()))
                .toList();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getAggregateType()).isEqualTo(orderEventsTopic);
        assertThat(row.getType()).isEqualTo("ORDER_CANCELLATION_REQUESTED");
        assertThat(row.getPayload()).contains("ORDER_CANCELLATION_REQUESTED");
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelOrder throws ResourceNotFoundException when order does not exist")
    void cancelOrder_throwsWhenOrderNotFound() {
        assertThatThrownBy(() -> cancelOrderUseCase.cancelOrder("non-existent-order-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelOrder throws UnprocessableRequestException when order is already CONFIRMED")
    void cancelOrder_throwsWhenOrderAlreadyConfirmed() {
        var order = placeOrder();
        sagaEventHandler.onShipmentCreated(order.getResourceId());

        var orderId = order.getResourceId();
        assertThatThrownBy(() -> cancelOrderUseCase.cancelOrder(orderId))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    @DisplayName("cancelOrder throws UnprocessableRequestException when order is already CANCELLED")
    void cancelOrder_throwsWhenOrderAlreadyCancelled() {
        var order = placeOrder();
        sagaEventHandler.onInventoryReservationFailed(order.getResourceId());

        var orderId = order.getResourceId();
        assertThatThrownBy(() -> cancelOrderUseCase.cancelOrder(orderId))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("CANCELLED");
    }

    private Order placeOrder() {
        return placeOrderUseCase.placeOrder(new PlaceOrderCommand(
                UUID.randomUUID().toString(),
                "customer-1",
                List.of(new PlaceOrderCommand.Item("product-1", 2, BigDecimal.valueOf(25))),
                "123 Main St, Springfield, IL 62701"));
    }
}
