package zen.labs.payment.adapter.inbound.messaging;

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
import zen.labs.payment.application.port.inbound.HandlePaymentEventUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("payment ShippingEventListener")
class ShippingEventListenerTest {

    @Mock
    private HandlePaymentEventUseCase eventUseCase;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private ShippingEventListener listener;

    private static final String ORDER_ID = "order-001";
    private static final String PAYLOAD = "{}";

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("should call handleShipmentFailed for SHIPMENT_FAILED")
        void shouldHandleShipmentFailed() throws Exception {
            doReturn(new SagaEvent.ShipmentFailedEvent("evt-1", ORDER_ID, "address invalid"))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handleShipmentFailed(ORDER_ID);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should not call use case for unrelated event type")
        void shouldIgnoreUnrelatedEventType() throws Exception {
            doReturn(new SagaEvent.ShipmentCreatedEvent("evt-1", ORDER_ID))
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
