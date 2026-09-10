package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOrderService")
class CancelOrderServiceTest {

    @Mock
    private OrderDataStore orderDataStore;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private CancelOrderService service;

    private static final String ORDER_ID = "order-001";

    private Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .resourceId(ORDER_ID)
                .customerId("cust-1")
                .status(status)
                .shippingAddress("123 St")
                .items(List.of())
                .build();
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should update order to CANCELLING and publish ORDER_CANCELLATION_REQUESTED when CONFIRMING")
        void shouldCancelWhenConfirming() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            service.cancelOrder(ORDER_ID);

            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLING);
            assertThat(captor.getValue().getResourceId()).isEqualTo(ORDER_ID);
            verify(eventPublisher).publishCancellationRequested(ORDER_ID);
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when order is CONFIRMED")
        void shouldReject_whenConfirmed() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMED)));

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID)).isInstanceOf(UnprocessableRequestException.class);

            verify(eventPublisher, never()).publishCancellationRequested(any());
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when order is CANCELLED")
        void shouldReject_whenAlreadyCancelled() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLED)));

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID)).isInstanceOf(UnprocessableRequestException.class);
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when order is CANCELLING")
        void shouldReject_whenAlreadyCancelling() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLING)));

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID)).isInstanceOf(UnprocessableRequestException.class);

            verify(eventPublisher, never()).publishCancellationRequested(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order not found")
        void shouldThrow_whenOrderNotFound() {
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID)).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
