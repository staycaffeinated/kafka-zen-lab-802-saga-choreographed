package zen.labs.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
import zen.labs.inventory.application.port.inbound.OrderedItem;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.domain.InventoryReservation;
import zen.labs.inventory.domain.ReservationStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryEventService")
class InventoryEventServiceTest {

    @Mock
    private InventoryReservationDataStore reservationDataStore;

    @Mock
    private InventoryEventPublisher eventPublisher;

    @InjectMocks
    private InventoryEventService service;

    private static final String ORDER_ID = "order-001";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(50);
    private static final String SHIPPING_ADDRESS = "123 Main St";

    @Nested
    @DisplayName("handleOrderPlaced")
    class HandleOrderPlaced {

        @Test
        @DisplayName("should save reservation and publish INVENTORY_RESERVED when all items within limit")
        void shouldReserveAndPublish_whenWithinLimit() {
            var items = List.of(new OrderedItem("prod-A", 10), new OrderedItem("prod-B", 100));
            when(reservationDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleOrderPlaced(ORDER_ID, items, AMOUNT, SHIPPING_ADDRESS);

            ArgumentCaptor<InventoryReservation> captor = ArgumentCaptor.forClass(InventoryReservation.class);
            verify(reservationDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
            verify(eventPublisher).publishInventoryReserved(ORDER_ID, AMOUNT, SHIPPING_ADDRESS);
        }

        @Test
        @DisplayName("should skip save and publish when reservation already exists (idempotent duplicate)")
        void shouldSkipSaveAndPublish_whenReservationAlreadyExists() {
            var existing = InventoryReservation.builder()
                    .orderId(ORDER_ID)
                    .status(ReservationStatus.RESERVED)
                    .reservedAt(Instant.parse("2026-07-01T13:00:00.00Z"))
                    .build();
            when(reservationDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderPlaced(ORDER_ID, List.of(new OrderedItem("prod-A", 1)), AMOUNT, SHIPPING_ADDRESS);

            verify(reservationDataStore, never()).save(any());
            verify(eventPublisher, never()).publishInventoryReserved(any(), any(), any());
            verify(eventPublisher, never()).publishInventoryReservationFailed(any(), any());
        }

        @Test
        @DisplayName("should NOT save and publish INVENTORY_RESERVATION_FAILED when any item exceeds 100")
        void shouldFailAndPublish_whenItemExceedsLimit() {
            var items = List.of(new OrderedItem("prod-X", 101));

            service.handleOrderPlaced(ORDER_ID, items, AMOUNT, SHIPPING_ADDRESS);

            verify(reservationDataStore, never()).save(any());
            verify(eventPublisher).publishInventoryReservationFailed(eq(ORDER_ID), any());
        }
    }

    @Nested
    @DisplayName("handleRelease")
    class HandleRelease {

        @Test
        @DisplayName("should update status to RELEASED and publish INVENTORY_RELEASED when reservation found")
        void shouldReleaseAndPublish_whenReservationFound() {
            var existing = InventoryReservation.builder()
                    .orderId(ORDER_ID)
                    .status(ReservationStatus.RESERVED)
                    .reservedAt(Instant.parse("2020-01-01T00:00:00.00Z"))
                    .build();
            when(reservationDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleRelease(ORDER_ID);

            verify(reservationDataStore).update(any());
            verify(eventPublisher).publishInventoryReleased(ORDER_ID);
        }

        @Test
        @DisplayName("should still publish INVENTORY_RELEASED when no reservation found")
        void shouldStillPublish_whenNoReservation() {
            when(reservationDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleRelease(ORDER_ID);

            verify(reservationDataStore, never()).update(any());
            verify(eventPublisher).publishInventoryReleased(ORDER_ID);
        }

        @Test
        @DisplayName("should skip update and publish when reservation already released (idempotent duplicate)")
        void shouldSkip_whenReservationAlreadyReleased() {
            var existing = InventoryReservation.builder()
                    .orderId(ORDER_ID)
                    .status(ReservationStatus.RELEASED)
                    .reservedAt(Instant.parse("2020-01-01T00:00:00.00Z"))
                    .build();
            when(reservationDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleRelease(ORDER_ID);

            verify(reservationDataStore, never()).update(any());
            verify(eventPublisher, never()).publishInventoryReleased(any());
        }
    }
}
