package zen.labs.payment.adapter.inbound.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
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
@DisplayName("payment InventoryEventListener")
class InventoryEventListenerTest {

    @Mock
    private HandlePaymentEventUseCase eventUseCase;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private InventoryEventListener listener;

    private static final String ORDER_ID = "order-001";
    private static final String ADDRESS = "123 Main St, Springfield, IL 62701";
    private static final String PAYLOAD = "{}";
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("should call handleInventoryReserved for INVENTORY_RESERVED")
        void shouldHandleInventoryReserved() throws Exception {
            doReturn(new SagaEvent.InventoryReservedEvent("evt-1", ORDER_ID, AMOUNT, ADDRESS))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handleInventoryReserved(ORDER_ID, AMOUNT, ADDRESS);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should not call use case for unrelated event type")
        void shouldIgnoreUnrelatedEventType() throws Exception {
            doReturn(new SagaEvent.InventoryReservationFailedEvent("evt-1", ORDER_ID, "out of stock"))
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
