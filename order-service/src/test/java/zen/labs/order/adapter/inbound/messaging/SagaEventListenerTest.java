package zen.labs.order.adapter.inbound.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaEventListener")
class SagaEventListenerTest {

    @Mock
    private HandleSagaEventUseCase eventHandler;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private SagaEventListener listener;

    private static final String ORDER_ID = "order-001";
    private static final String PAYLOAD = "{}";

    private SagaEvent eventOf(SagaEventType type) {
        return new SagaEvent("evt-1", type, ORDER_ID, null, List.of(), BigDecimal.TEN, "123 Main St");
    }

    /* ---------------------------------------------------------------------------------
     * dispatch
     * --------------------------------------------------------------------------------- */

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("should call onInventoryReservationFailed for INVENTORY_RESERVATION_FAILED")
        void shouldHandleInventoryReservationFailed() throws Exception {
            doReturn(eventOf(SagaEventType.INVENTORY_RESERVATION_FAILED))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventHandler).onInventoryReservationFailed(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should call onInventoryReleased for INVENTORY_RELEASED")
        void shouldHandleInventoryReleased() throws Exception {
            doReturn(eventOf(SagaEventType.INVENTORY_RELEASED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventHandler).onInventoryReleased(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should call onShipmentCreated for SHIPMENT_CREATED")
        void shouldHandleShipmentCreated() throws Exception {
            doReturn(eventOf(SagaEventType.SHIPMENT_CREATED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventHandler).onShipmentCreated(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should not call handler for unrelated event type")
        void shouldIgnoreUnrelatedEventType() throws Exception {
            doReturn(eventOf(SagaEventType.ORDER_PLACED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verifyNoInteractions(eventHandler);
            verify(ack).acknowledge();
        }
    }

    /* ---------------------------------------------------------------------------------
     * error handling
     * --------------------------------------------------------------------------------- */

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should propagate exception and not acknowledge when payload cannot be parsed")
        void shouldPropagateException_whenParseFails() throws Exception {
            doThrow(new RuntimeException("bad json")).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            assertThatThrownBy(() -> listener.onEvent(PAYLOAD, ack))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("bad json");

            verifyNoInteractions(eventHandler);
            verify(ack, never()).acknowledge();
        }
    }
}
