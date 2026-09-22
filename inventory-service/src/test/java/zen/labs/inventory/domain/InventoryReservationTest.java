/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InventoryReservation")
class InventoryReservationTest {

    private static final String ORDER_ID = "order-001";

    // ---------------------------------------------------------------------------------
    // forOrder() factory tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("forOrder")
    class ForOrder {

        @Test
        @DisplayName("should create reservation with RESERVED status and correct orderId")
        void shouldCreateReservationWithReservedStatus() {
            InventoryReservation reservation = InventoryReservation.forOrder(ORDER_ID);

            assertThat(reservation.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(reservation.getReservedAt()).isNotNull();
        }
    }

    // ---------------------------------------------------------------------------------
    // release() tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        @DisplayName("should transition to RELEASED when status is RESERVED")
        void shouldTransitionToReleased_whenStatusIsReserved() {
            InventoryReservation reservation = InventoryReservation.forOrder(ORDER_ID);

            InventoryReservation result = reservation.release();

            assertThat(result.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        }

        @Test
        @DisplayName("should return new instance and not mutate original")
        void shouldReturnNewInstance_andNotMutateOriginal() {
            InventoryReservation reservation = InventoryReservation.forOrder(ORDER_ID);

            InventoryReservation result = reservation.release();

            assertThat(result).isNotSameAs(reservation);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        }

        @Test
        @DisplayName("should throw IllegalStateException when status is already RELEASED")
        void shouldThrow_whenStatusIsAlreadyReleased() {
            InventoryReservation released = InventoryReservation.builder()
                    .orderId(ORDER_ID)
                    .status(ReservationStatus.RELEASED)
                    .reservedAt(java.time.Instant.parse("2026-07-01T08:09:10Z"))
                    .build();

            assertThatThrownBy(released::release)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(ReservationStatus.RELEASED.name());
        }
    }
}
