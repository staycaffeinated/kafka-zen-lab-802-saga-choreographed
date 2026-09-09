/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.order.application.port.inbound.HandleSagaReplyUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaReplyListener")
class SagaReplyListenerTest {

    @Mock
    private HandleSagaReplyUseCase handleSagaReplyUseCase;

    @Mock
    private Acknowledgment ack;

    private SagaReplyListener listener;

    /**
     * A real JsonMapper is used to produce test payloads. The SagaReply
     * record is a plain data carrier with no custom serialization, so a
     * default JsonMapper instance is sufficient.
     */
    private final JsonMapper jsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        listener = new SagaReplyListener(handleSagaReplyUseCase, jsonMapper);
    }

    // -----------------------------------------------------------------------
    // Helper: serialise a SagaReply to a JSON string
    // -----------------------------------------------------------------------

    private String toJson(SagaReply reply) {
        try {
            return jsonMapper.writeValueAsString(reply);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialise SagaReply for test setup", e);
        }
    }

    // -----------------------------------------------------------------------
    // Inventory reply tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Inventory replies")
    class InventoryReplies {

        @Test
        @DisplayName("INVENTORY_RESERVED should call onInventoryReply with success=true")
        void shouldCallOnInventoryReply_whenInventoryReserved() {
            // Given
            SagaReply reply = new SagaReply("saga-1", "order-1", "INVENTORY_RESERVED", true, null);
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onInventoryReply("saga-1", "order-1", true, null);
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("INVENTORY_RESERVATION_FAILED should call onInventoryReply with success=false")
        void shouldCallOnInventoryReply_whenInventoryReservationFailed() {
            // Given
            SagaReply reply = new SagaReply("saga-2", "order-2", "INVENTORY_RESERVATION_FAILED", false, "Out of stock");
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onInventoryReply("saga-2", "order-2", false, "Out of stock");
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("INVENTORY_RELEASED should call onInventoryReleasedReply")
        void shouldCallOnInventoryReleasedReply_whenInventoryReleased() {
            // Given
            SagaReply reply = new SagaReply("saga-3", "order-3", "INVENTORY_RELEASED", true, null);
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onInventoryReleasedReply("saga-3", "order-3");
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }
    }

    // -----------------------------------------------------------------------
    // Payment reply tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Payment replies")
    class PaymentReplies {

        @Test
        @DisplayName("PAYMENT_PROCESSED should call onPaymentReply with success=true")
        void shouldCallOnPaymentReply_whenPaymentProcessed() {
            // Given
            SagaReply reply = new SagaReply("saga-4", "order-4", "PAYMENT_PROCESSED", true, null);
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onPaymentReply("saga-4", "order-4", true, null);
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("PAYMENT_FAILED should call onPaymentReply with success=false")
        void shouldCallOnPaymentReply_whenPaymentFailed() {
            // Given
            SagaReply reply = new SagaReply("saga-5", "order-5", "PAYMENT_FAILED", false, "Insufficient funds");
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onPaymentReply("saga-5", "order-5", false, "Insufficient funds");
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("PAYMENT_REFUNDED should call onPaymentRefundedReply")
        void shouldCallOnPaymentRefundedReply_whenPaymentRefunded() {
            // Given
            SagaReply reply = new SagaReply("saga-6", "order-6", "PAYMENT_REFUNDED", true, null);
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onPaymentRefundedReply("saga-6", "order-6");
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }
    }

    // -----------------------------------------------------------------------
    // Shipment reply tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Shipment replies")
    class ShipmentReplies {

        @Test
        @DisplayName("SHIPMENT_CREATED should call onShipmentReply with success=true")
        void shouldCallOnShipmentReply_whenShipmentCreated() {
            // Given
            SagaReply reply = new SagaReply("saga-7", "order-7", "SHIPMENT_CREATED", true, null);
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onShipmentReply("saga-7", "order-7", true, null);
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("SHIPMENT_FAILED should call onShipmentReply with success=false")
        void shouldCallOnShipmentReply_whenShipmentFailed() {
            // Given
            SagaReply reply = new SagaReply("saga-8", "order-8", "SHIPMENT_FAILED", false, "Address not found");
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verify(handleSagaReplyUseCase).onShipmentReply("saga-8", "order-8", false, "Address not found");
            verifyNoMoreInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Unknown reply type should not invoke any use-case method, but ack must still be called")
        void shouldNotCallAnyUseCaseMethod_whenReplyTypeIsUnknown() {
            // Given
            SagaReply reply = new SagaReply("saga-9", "order-9", "COMPLETELY_UNKNOWN_TYPE", true, null);
            String payload = toJson(reply);

            // When
            listener.onReply(payload, ack);

            // Then
            verifyNoInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Malformed JSON payload should be handled gracefully and ack must still be called")
        void shouldHandleException_whenPayloadIsMalformedJson() throws Exception {
            // Given — use a mock JsonMapper that throws on read to simulate malformed input
            JsonMapper brokenMapper = mock(JsonMapper.class);
            when(brokenMapper.readValue("{bad-json}", SagaReply.class))
                    .thenThrow(new RuntimeException("Simulated parse failure"));

            SagaReplyListener listenerWithBrokenMapper = new SagaReplyListener(handleSagaReplyUseCase, brokenMapper);

            // When
            listenerWithBrokenMapper.onReply("{bad-json}", ack);

            // Then
            verifyNoInteractions(handleSagaReplyUseCase);
            verify(ack).acknowledge();
        }
    }
}
