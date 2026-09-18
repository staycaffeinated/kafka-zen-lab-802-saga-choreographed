package zen.labs.shipping.adapter.inbound.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import zen.labs.shipping.application.port.inbound.HandleShipmentEventUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("shipping PaymentEventListener")
class PaymentEventListenerTest {

    @Mock
    private HandleShipmentEventUseCase eventUseCase;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PaymentEventListener listener;

    private static final String ORDER_ID = "order-001";
    private static final String ADDRESS = "123 Main St, Springfield, IL 62701";
    private static final String PAYLOAD = "{}";

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("should call handlePaymentProcessed for PAYMENT_PROCESSED")
        void shouldHandlePaymentProcessed() throws Exception {
            doReturn(new SagaEvent.PaymentProcessedEvent("evt-1", ORDER_ID, ADDRESS))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handlePaymentProcessed(ORDER_ID, ADDRESS);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should not call use case for unrelated event type")
        void shouldIgnoreUnrelatedEventType() throws Exception {
            doReturn(new SagaEvent.PaymentFailedEvent("evt-1", ORDER_ID, "declined"))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verifyNoInteractions(eventUseCase);
            verify(ack).acknowledge();
        }
    }

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

            verifyNoInteractions(eventUseCase);
            verify(ack, never()).acknowledge();
        }
    }
}
