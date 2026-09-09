package zen.labs.shipping.adapter.inbound.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.shipping.application.port.inbound.HandleShipmentCommandUseCase;

@ExtendWith(MockitoExtension.class)
class SagaCommandListenerTest {

    @Mock
    private HandleShipmentCommandUseCase handleShipmentCommandUseCase;

    @Mock
    private Acknowledgment ack;

    private SagaCommandListener listener;
    private final JsonMapper jsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        listener = new SagaCommandListener(handleShipmentCommandUseCase, jsonMapper);
    }

    @Nested
    @DisplayName("onCommand — CREATE_SHIPMENT")
    class CreateShipmentCommand {

        private static final String SAGA_ID = "saga-42";
        private static final String ORDER_ID = "order-99";
        private static final String SHIPPING_ADDRESS = "123 Main St, Springfield, IL 62701";

        @Test
        @DisplayName("should delegate to createShipment with correct sagaId, orderId, and shippingAddress")
        void shouldDelegateToCreateShipment() throws Exception {
            // given
            SagaCommand cmd = new SagaCommand(
                    SAGA_ID,
                    ORDER_ID,
                    "CREATE_SHIPMENT",
                    List.of(new SagaCommandItem("prod-1", 2)),
                    new BigDecimal("49.99"),
                    SHIPPING_ADDRESS);
            String payload = jsonMapper.writeValueAsString(cmd);

            // when
            listener.onCommand(payload, ack);

            // then
            verify(handleShipmentCommandUseCase).createShipment(SAGA_ID, ORDER_ID, SHIPPING_ADDRESS);
        }

        @Test
        @DisplayName("should acknowledge the message after a CREATE_SHIPMENT command")
        void shouldAcknowledgeAfterCreateShipment() throws Exception {
            // given
            SagaCommand cmd = new SagaCommand(
                    SAGA_ID, ORDER_ID, "CREATE_SHIPMENT", List.of(), new BigDecimal("10.00"), SHIPPING_ADDRESS);
            String payload = jsonMapper.writeValueAsString(cmd);

            // when
            listener.onCommand(payload, ack);

            // then
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("onCommand — unknown command type")
    class UnknownCommandType {

        @Test
        @DisplayName("should not interact with the use case for an unknown command type")
        void shouldNotCallUseCaseForUnknownCommandType() throws Exception {
            // given
            SagaCommand cmd = new SagaCommand("saga-1", "order-1", "CANCEL_SHIPMENT", List.of(), null, "456 Elm Ave");
            String payload = jsonMapper.writeValueAsString(cmd);

            // when
            listener.onCommand(payload, ack);

            // then
            verifyNoInteractions(handleShipmentCommandUseCase);
        }

        @Test
        @DisplayName("should still acknowledge the message for an unknown command type")
        void shouldAcknowledgeForUnknownCommandType() throws Exception {
            // given
            SagaCommand cmd = new SagaCommand("saga-1", "order-1", "CANCEL_SHIPMENT", List.of(), null, "456 Elm Ave");
            String payload = jsonMapper.writeValueAsString(cmd);

            // when
            listener.onCommand(payload, ack);

            // then
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("onCommand — malformed JSON")
    class MalformedJson {

        @Test
        @DisplayName("should not call the use case when the payload is malformed JSON")
        void shouldNotCallUseCaseWhenPayloadIsMalformed() {
            // given
            String malformedPayload = "{ this is not valid json }";

            // when
            listener.onCommand(malformedPayload, ack);

            // then
            verifyNoInteractions(handleShipmentCommandUseCase);
        }

        @Test
        @DisplayName("should still acknowledge the message even when the payload is malformed")
        void shouldAcknowledgeEvenWhenPayloadIsMalformed() {
            // given
            String malformedPayload = "not-json-at-all";

            // when
            listener.onCommand(malformedPayload, ack);

            // then
            verify(ack).acknowledge();
        }
    }
}
