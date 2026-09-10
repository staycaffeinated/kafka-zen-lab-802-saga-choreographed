package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOrderService")
class PlaceOrderServiceTest {

    @Mock
    private OrderDataStore orderDataStore;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private ResourceIdSupplier resourceIdSupplier;

    @Mock
    private IdempotencyKeyStore idempotencyKeyStore;

    @InjectMocks
    private PlaceOrderService service;

    private static final String ORDER_ID = "order-001";
    private static final String CUSTOMER_ID = "cust-001";
    private static final String SHIPPING_ADDRESS = "123 Main St";

    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @Test
        @DisplayName("should save order with CONFIRMING status and publish ORDER_PLACED")
        void shouldSaveAndPublish() {
            var cmd = new PlaceOrderCommand(
                    null,
                    CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item("prod-A", 2, BigDecimal.valueOf(10))),
                    SHIPPING_ADDRESS);
            when(resourceIdSupplier.nextResourceId()).thenReturn(ORDER_ID);
            var saved = Order.builder()
                    .resourceId(ORDER_ID)
                    .customerId(CUSTOMER_ID)
                    .status(OrderStatus.CONFIRMING)
                    .shippingAddress(SHIPPING_ADDRESS)
                    .items(List.of(new OrderItem("prod-A", 2, BigDecimal.valueOf(10))))
                    .build();
            when(orderDataStore.save(any())).thenReturn(saved);

            var result = service.placeOrder(cmd);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            verify(eventPublisher)
                    .publishOrderPlaced(eq(ORDER_ID), any(), eq(BigDecimal.valueOf(20)), eq(SHIPPING_ADDRESS));
        }

        @Test
        @DisplayName("should return existing order and skip publish when idempotency key already exists")
        void shouldReturnExistingOrder_whenIdempotencyKeyPresent() {
            var cmd = new PlaceOrderCommand(
                    "idem-key-1",
                    CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item("prod-A", 1, BigDecimal.TEN)),
                    SHIPPING_ADDRESS);
            var existing = Order.builder()
                    .resourceId(ORDER_ID)
                    .customerId(CUSTOMER_ID)
                    .status(OrderStatus.CONFIRMED)
                    .items(List.of())
                    .shippingAddress(SHIPPING_ADDRESS)
                    .build();
            when(idempotencyKeyStore.findOrderId("idem-key-1")).thenReturn(Optional.of(ORDER_ID));
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.of(existing));

            var result = service.placeOrder(cmd);

            assertThat(result.getResourceId()).isEqualTo(ORDER_ID);
            verify(orderDataStore, never()).save(any());
            verify(eventPublisher, never()).publishOrderPlaced(any(), any(), any(), any());
        }
    }
}
