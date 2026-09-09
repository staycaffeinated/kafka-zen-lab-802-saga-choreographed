package zen.labs.shipping.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.shipping.application.port.outbound.SagaReplyPublisher;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@ExtendWith(MockitoExtension.class)
class ShipmentSagaParticipantTest {

    @Mock
    private ShipmentDataStore shipmentDataStore;

    @Mock
    private SagaReplyPublisher replyPublisher;

    @InjectMocks
    private ShipmentSagaParticipant shipmentSagaParticipant;

    @Nested
    @DisplayName("createShipment")
    class CreateShipment {

        private static final String SAGA_ID = "saga-001";
        private static final String ORDER_ID = "order-001";
        private static final String SHIPPING_ADDRESS = "123 Main St, Springfield, IL 62701";

        @Test
        @DisplayName("should save shipment with CREATED status and all provided fields")
        void shouldSaveShipmentWithCreatedStatusAndAllFields() {
            // given
            when(shipmentDataStore.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, SHIPPING_ADDRESS);

            // then
            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentDataStore).save(captor.capture());

            Shipment saved = captor.getValue();
            assertThat(saved.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(saved.getShippingAddress()).isEqualTo(SHIPPING_ADDRESS);
            assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        }

        @Test
        @DisplayName("should set a non-null createdAt timestamp on the saved shipment")
        void shouldSetNonNullCreatedAtOnSavedShipment() {
            // given
            when(shipmentDataStore.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, SHIPPING_ADDRESS);

            // then
            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentDataStore).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should call shipmentDataStore.save exactly once")
        void shouldCallSaveExactlyOnce() {
            // given
            when(shipmentDataStore.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, SHIPPING_ADDRESS);

            // then
            verify(shipmentDataStore, times(1)).save(any(Shipment.class));
        }

        @Test
        @DisplayName("should publish SHIPMENT_CREATED reply with success=true and null failureReason")
        void shouldPublishShipmentCreatedReplyWithSuccessTrue() {
            // given
            when(shipmentDataStore.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, SHIPPING_ADDRESS);

            // then
            verify(replyPublisher).publish("SHIPMENT_CREATED", SAGA_ID, ORDER_ID, true, null);
        }

        @Test
        @DisplayName("should publish reply exactly once")
        void shouldPublishReplyExactlyOnce() {
            // given
            when(shipmentDataStore.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, SHIPPING_ADDRESS);

            // then
            verify(replyPublisher, times(1)).publish(any(), any(), any(), any(Boolean.class), any());
        }

        @Test
        @DisplayName("should succeed when address is exactly 200 characters (boundary)")
        void shouldSucceed_whenAddressIsExactly200Chars() {
            // given
            String address = "A".repeat(ShipmentSagaParticipant.MAX_ADDRESS_LENGTH);
            when(shipmentDataStore.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, address);

            // then
            verify(shipmentDataStore).save(any(Shipment.class));
            verify(replyPublisher).publish("SHIPMENT_CREATED", SAGA_ID, ORDER_ID, true, null);
        }

        @Test
        @DisplayName("should publish SHIPMENT_FAILED and never save when address exceeds 200 characters")
        void shouldPublishShipmentFailed_andNeverSave_whenAddressExceedsMaxLength() {
            // given
            String address = "A".repeat(ShipmentSagaParticipant.MAX_ADDRESS_LENGTH + 1);

            // when
            shipmentSagaParticipant.createShipment(SAGA_ID, ORDER_ID, address);

            // then
            verify(shipmentDataStore, never()).save(any());
            verify(replyPublisher)
                    .publish(
                            "SHIPMENT_FAILED",
                            SAGA_ID,
                            ORDER_ID,
                            false,
                            "Shipping address exceeds maximum length of " + ShipmentSagaParticipant.MAX_ADDRESS_LENGTH);
        }
    }
}
