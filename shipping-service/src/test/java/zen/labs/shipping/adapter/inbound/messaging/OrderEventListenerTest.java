package zen.labs.shipping.adapter.inbound.messaging;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
import zen.labs.shipping.application.port.inbound.HandleShipmentEventUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("shipping OrderEventListener")
class OrderEventListenerTest {

    @Mock
    private HandleShipmentEventUseCase eventUseCase;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private OrderEventListener listener;

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
        @DisplayName("should call handleOrderCancellationRequested for ORDER_CANCELLATION_REQUESTED")
        void shouldHandleOrderCancellationRequested() throws Exception {
            doReturn(eventOf(SagaEventType.ORDER_CANCELLATION_REQUESTED))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handleOrderCancellationRequested(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should not call use case for unrelated event type")
        void shouldIgnoreUnrelatedEventType() throws Exception {
            doReturn(eventOf(SagaEventType.ORDER_PLACED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verifyNoInteractions(eventUseCase);
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
        @DisplayName("should acknowledge and not propagate exception when payload cannot be parsed")
        void shouldAckEvenOnParseFailure() throws Exception {
            doThrow(new RuntimeException("bad json")).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verifyNoInteractions(eventUseCase);
            verify(ack).acknowledge();
        }
    }
}
