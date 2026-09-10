package zen.labs.inventory.adapter.inbound.messaging;

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
import zen.labs.inventory.application.port.inbound.HandleInventoryEventUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("inventory PaymentEventListener")
class PaymentEventListenerTest {

    @Mock
    private HandleInventoryEventUseCase eventUseCase;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PaymentEventListener listener;

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
        @DisplayName("should call handleRelease for PAYMENT_FAILED")
        void shouldHandlePaymentFailed() throws Exception {
            doReturn(eventOf(SagaEventType.PAYMENT_FAILED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handleRelease(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should call handleRelease for PAYMENT_REFUNDED")
        void shouldHandlePaymentRefunded() throws Exception {
            doReturn(eventOf(SagaEventType.PAYMENT_REFUNDED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handleRelease(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should not call use case for unrelated event type")
        void shouldIgnoreUnrelatedEventType() throws Exception {
            doReturn(eventOf(SagaEventType.PAYMENT_PROCESSED)).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

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
