package zen.labs.shipping.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShipmentEventService")
class ShipmentEventServiceTest {

    @Mock
    private ShipmentDataStore shipmentDataStore;

    @Mock
    private ShippingEventPublisher eventPublisher;

    @InjectMocks
    private ShipmentEventService service;

    private static final String ORDER_ID = "order-001";
    static final int MAX_ADDRESS_LENGTH = 200;

    @Nested
    @DisplayName("handlePaymentProcessed")
    class HandlePaymentProcessed {

        @Test
        @DisplayName("should save shipment and publish SHIPMENT_CREATED when address within limit")
        void shouldCreateAndPublish_whenAddressWithinLimit() {
            String address = "123 Main St";
            when(shipmentDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handlePaymentProcessed(ORDER_ID, address);

            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ShipmentStatus.CREATED);
            verify(eventPublisher).publishShipmentCreated(ORDER_ID);
        }

        @Test
        @DisplayName("should skip save and publish when shipment already exists (idempotent duplicate)")
        void shouldSkipSaveAndPublish_whenShipmentAlreadyExists() {
            var existing = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress("123 Main St")
                    .status(ShipmentStatus.CREATED)
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(shipmentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handlePaymentProcessed(ORDER_ID, "123 Main St");

            verify(shipmentDataStore, never()).save(any());
            verify(eventPublisher, never()).publishShipmentCreated(any());
            verify(eventPublisher, never()).publishShipmentFailed(any(), any());
        }

        @Test
        @DisplayName("should NOT save and publish SHIPMENT_FAILED when address exceeds limit")
        void shouldFailAndPublish_whenAddressTooLong() {
            String longAddress = "A".repeat(MAX_ADDRESS_LENGTH + 1);

            service.handlePaymentProcessed(ORDER_ID, longAddress);

            verify(shipmentDataStore, never()).save(any());
            verify(eventPublisher).publishShipmentFailed(eq(ORDER_ID), any());
        }

        @Test
        @DisplayName("should publish SHIPMENT_FAILED when shippingAddress is null")
        void shouldFailAndPublish_whenAddressIsNull() {
            service.handlePaymentProcessed(ORDER_ID, null);

            verify(shipmentDataStore, never()).save(any());
            verify(eventPublisher).publishShipmentFailed(eq(ORDER_ID), any());
        }

        @Test
        @DisplayName("should publish SHIPMENT_FAILED when shippingAddress is blank")
        void shouldFailAndPublish_whenAddressIsBlank() {
            service.handlePaymentProcessed(ORDER_ID, "   ");

            verify(shipmentDataStore, never()).save(any());
            verify(eventPublisher).publishShipmentFailed(eq(ORDER_ID), any());
        }

        @Test
        @DisplayName("address exactly at limit should succeed")
        void shouldSucceed_atExactLimit() {
            String address = "A".repeat(MAX_ADDRESS_LENGTH);
            when(shipmentDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handlePaymentProcessed(ORDER_ID, address);

            verify(eventPublisher).publishShipmentCreated(ORDER_ID);
        }
    }

    @Nested
    @DisplayName("handleOrderCancellationRequested")
    class HandleOrderCancellationRequested {

        @Test
        @DisplayName("should update shipment to CANCELLED when shipment exists")
        void shouldCancelShipment_whenExists() {
            var existing = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress("123 St")
                    .status(ShipmentStatus.CREATED)
                    .createdAt(Instant.parse("2026-02-20T00:00:00Z"))
                    .build();
            when(shipmentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderCancellationRequested(ORDER_ID);

            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentDataStore).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("should do nothing when no shipment exists")
        void shouldDoNothing_whenNoShipment() {
            when(shipmentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(shipmentDataStore, never()).update(any());
        }

        @Test
        @DisplayName("should skip update when shipment already cancelled (idempotent duplicate)")
        void shouldSkip_whenShipmentAlreadyCancelled() {
            var existing = Shipment.builder()
                    .orderId(ORDER_ID)
                    .shippingAddress("123 St")
                    .status(ShipmentStatus.CANCELLED)
                    .createdAt(Instant.parse("2026-02-20T00:00:00Z"))
                    .build();
            when(shipmentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(shipmentDataStore, never()).update(any());
        }
    }
}
