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
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaEventHandler")
class SagaEventHandlerTest {

    @Mock
    private OrderDataStore orderDataStore;

    @InjectMocks
    private SagaEventHandler handler;

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
    @DisplayName("onInventoryReservationFailed")
    class OnInventoryReservationFailed {

        @Test
        @DisplayName("should mark order CANCELLED")
        void shouldMarkCancelled() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            handler.onInventoryReservationFailed(ORDER_ID);

            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(captor.getValue().getResourceId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order not found")
        void shouldThrow_whenOrderNotFound() {
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.onInventoryReservationFailed(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderDataStore, never()).update(any());
        }

        @Test
        @DisplayName("should skip when order already cancelled (idempotent duplicate)")
        void shouldSkip_whenOrderAlreadyCancelled() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLED)));

            handler.onInventoryReservationFailed(ORDER_ID);

            verify(orderDataStore, never()).update(any());
        }
    }

    @Nested
    @DisplayName("onInventoryReleased")
    class OnInventoryReleased {

        @Test
        @DisplayName("should mark order CANCELLED when CONFIRMING")
        void shouldMarkCancelled_whenConfirming() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            handler.onInventoryReleased(ORDER_ID);

            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should mark order CANCELLED when CANCELLING")
        void shouldMarkCancelled_whenCancelling() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLING)));

            handler.onInventoryReleased(ORDER_ID);

            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should NOT update order when already CONFIRMED")
        void shouldSkip_whenAlreadyConfirmed() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMED)));

            handler.onInventoryReleased(ORDER_ID);

            verify(orderDataStore, never()).update(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order not found")
        void shouldThrow_whenOrderNotFound() {
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.onInventoryReleased(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderDataStore, never()).update(any());
        }

        @Test
        @DisplayName("should skip when order already cancelled (idempotent duplicate)")
        void shouldSkip_whenOrderAlreadyCancelled() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLED)));

            handler.onInventoryReleased(ORDER_ID);

            verify(orderDataStore, never()).update(any());
        }
    }

    @Nested
    @DisplayName("onShipmentCreated")
    class OnShipmentCreated {

        @Test
        @DisplayName("should mark order CONFIRMED when CONFIRMING")
        void shouldMarkConfirmed_whenConfirming() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            handler.onShipmentCreated(ORDER_ID);

            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("should NOT update order when CANCELLING (race condition guard)")
        void shouldSkip_whenCancelling() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLING)));

            handler.onShipmentCreated(ORDER_ID);

            verify(orderDataStore, never()).update(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order not found")
        void shouldThrow_whenOrderNotFound() {
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.onShipmentCreated(ORDER_ID)).isInstanceOf(ResourceNotFoundException.class);

            verify(orderDataStore, never()).update(any());
        }
    }
}
