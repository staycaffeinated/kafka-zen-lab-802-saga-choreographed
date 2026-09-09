/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import zen.labs.inventory.application.port.inbound.HandleInventoryCommandUseCase.Item;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.application.port.outbound.SagaReplyPublisher;
import zen.labs.inventory.domain.InventoryReservation;
import zen.labs.inventory.domain.ReservationStatus;

@ExtendWith(MockitoExtension.class)
class InventorySagaParticipantTest {

    @Mock
    private InventoryReservationDataStore reservationDataStore;

    @Mock
    private SagaReplyPublisher replyPublisher;

    @InjectMocks
    private InventorySagaParticipant participant;

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Nested
    @DisplayName("reserveInventory")
    class ReserveInventory {

        @Test
        @DisplayName(
                "should save reservation with RESERVED status and publish INVENTORY_RESERVED when all items within limit")
        void shouldSaveReservationAndPublishSuccess_whenAllItemsWithinLimit() {
            // Given
            String sagaId = "saga-001";
            String orderId = "order-001";
            List<Item> items = List.of(new Item("product-A", 10), new Item("product-B", 100));

            InventoryReservation savedReservation = InventoryReservation.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .status(ReservationStatus.RESERVED)
                    .reservedAt(FIXED_NOW)
                    .build();
            when(reservationDataStore.save(any(InventoryReservation.class))).thenReturn(savedReservation);

            // When
            participant.reserveInventory(sagaId, orderId, items);

            // Then
            ArgumentCaptor<InventoryReservation> reservationCaptor =
                    ArgumentCaptor.forClass(InventoryReservation.class);
            verify(reservationDataStore).save(reservationCaptor.capture());

            InventoryReservation captured = reservationCaptor.getValue();
            assertThat(captured.getSagaId()).isEqualTo(sagaId);
            assertThat(captured.getOrderId()).isEqualTo(orderId);
            assertThat(captured.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(captured.getReservedAt()).isNotNull();

            verify(replyPublisher).publish("INVENTORY_RESERVED", sagaId, orderId, true, null);
        }

        @Test
        @DisplayName(
                "should NOT save and should publish INVENTORY_RESERVATION_FAILED with product id when an item exceeds limit")
        void shouldNotSaveAndPublishFailure_whenItemExceedsLimit() {
            // Given
            String sagaId = "saga-002";
            String orderId = "order-002";
            List<Item> items = List.of(new Item("product-X", 101));

            // When
            participant.reserveInventory(sagaId, orderId, items);

            // Then
            verify(reservationDataStore, never()).save(any());
            verify(replyPublisher)
                    .publish(
                            "INVENTORY_RESERVATION_FAILED",
                            sagaId,
                            orderId,
                            false,
                            "Insufficient stock for product: product-X");
        }

        @Test
        @DisplayName("should fail on the exceeded item when first item is ok but second exceeds limit")
        void shouldFailOnExceededItem_whenFirstItemOkButSecondExceedsLimit() {
            // Given
            String sagaId = "saga-003";
            String orderId = "order-003";
            List<Item> items = List.of(new Item("product-ok", 50), new Item("product-exceeded", 101));

            // When
            participant.reserveInventory(sagaId, orderId, items);

            // Then
            verify(reservationDataStore, never()).save(any());
            verify(replyPublisher)
                    .publish(
                            "INVENTORY_RESERVATION_FAILED",
                            sagaId,
                            orderId,
                            false,
                            "Insufficient stock for product: product-exceeded");
        }

        @Test
        @DisplayName("should succeed when item quantity is exactly 100 (boundary)")
        void shouldSucceed_whenItemQuantityIsExactly100() {
            // Given
            String sagaId = "saga-004";
            String orderId = "order-004";
            List<Item> items = List.of(new Item("product-boundary", 100));

            InventoryReservation savedReservation = InventoryReservation.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .status(ReservationStatus.RESERVED)
                    .reservedAt(FIXED_NOW)
                    .build();
            when(reservationDataStore.save(any(InventoryReservation.class))).thenReturn(savedReservation);

            // When
            participant.reserveInventory(sagaId, orderId, items);

            // Then
            verify(reservationDataStore).save(any(InventoryReservation.class));
            verify(replyPublisher).publish("INVENTORY_RESERVED", sagaId, orderId, true, null);
        }

        @Test
        @DisplayName("should fail when item quantity is exactly 101 (boundary)")
        void shouldFail_whenItemQuantityIsExactly101() {
            // Given
            String sagaId = "saga-005";
            String orderId = "order-005";
            List<Item> items = List.of(new Item("product-over-limit", 101));

            // When
            participant.reserveInventory(sagaId, orderId, items);

            // Then
            verify(reservationDataStore, never()).save(any());
            verify(replyPublisher)
                    .publish(
                            "INVENTORY_RESERVATION_FAILED",
                            sagaId,
                            orderId,
                            false,
                            "Insufficient stock for product: product-over-limit");
        }
    }

    @Nested
    @DisplayName("releaseInventory")
    class ReleaseInventory {

        @Test
        @DisplayName("should update reservation to RELEASED and publish INVENTORY_RELEASED when reservation found")
        void shouldUpdateToReleasedAndPublish_whenReservationFound() {
            // Given
            String sagaId = "saga-006";
            String orderId = "order-006";
            InventoryReservation existing = InventoryReservation.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .status(ReservationStatus.RESERVED)
                    .reservedAt(FIXED_NOW)
                    .build();
            when(reservationDataStore.findBySagaId(sagaId)).thenReturn(Optional.of(existing));

            // When
            participant.releaseInventory(sagaId, orderId);

            // Then
            ArgumentCaptor<InventoryReservation> updateCaptor = ArgumentCaptor.forClass(InventoryReservation.class);
            verify(reservationDataStore).update(updateCaptor.capture());

            InventoryReservation updatedArg = updateCaptor.getValue();
            assertThat(updatedArg.getSagaId()).isEqualTo(sagaId);
            assertThat(updatedArg.getStatus()).isEqualTo(ReservationStatus.RELEASED);

            verify(replyPublisher).publish("INVENTORY_RELEASED", sagaId, orderId, true, null);
        }

        @Test
        @DisplayName("should still publish INVENTORY_RELEASED and never call update when reservation not found")
        void shouldPublishWithoutUpdate_whenReservationNotFound() {
            // Given
            String sagaId = "saga-007";
            String orderId = "order-007";
            when(reservationDataStore.findBySagaId(sagaId)).thenReturn(Optional.empty());

            // When
            participant.releaseInventory(sagaId, orderId);

            // Then
            verify(reservationDataStore, never()).update(any());
            verify(replyPublisher).publish("INVENTORY_RELEASED", sagaId, orderId, true, null);
        }
    }
}
