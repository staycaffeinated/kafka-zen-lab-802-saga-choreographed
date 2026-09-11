package zen.labs.inventory.adapter.inbound.messaging;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import zen.labs.inventory.application.port.inbound.HandleInventoryEventUseCase;

@ExtendWith(MockitoExtension.class)
@DisplayName("inventory OrderEventListener")
class OrderEventListenerTest {

    @Mock
    private HandleInventoryEventUseCase eventUseCase;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private OrderEventListener listener;

    private static final String ORDER_ID = "order-001";
    private static final String ADDRESS = "123 Main St, Springfield, IL 62701";
    private static final String PAYLOAD = "{}";
    private static final BigDecimal AMOUNT = new BigDecimal("99.99");

    private SagaEvent eventOf(SagaEventType type) {
        return new SagaEvent("evt-1", type, ORDER_ID, null, List.of(), AMOUNT, ADDRESS);
    }

    /* ---------------------------------------------------------------------------------
     * dispatch
     * --------------------------------------------------------------------------------- */

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("should call handleOrderPlaced for ORDER_PLACED")
        void shouldHandleOrderPlaced() throws Exception {
            var event = eventOf(SagaEventType.ORDER_PLACED);
            doReturn(event).when(jsonMapper).readValue(PAYLOAD, SagaEvent.class);

            listener.onEvent(PAYLOAD, ack);

            verify(eventUseCase).handleOrderPlaced(eq(ORDER_ID), anyList(), eq(AMOUNT), eq(ADDRESS));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should call handleRelease for ORDER_CANCELLATION_REQUESTED")
        void shouldHandleOrderCancellationRequested() throws Exception {
            doReturn(eventOf(SagaEventType.ORDER_CANCELLATION_REQUESTED))
                    .when(jsonMapper)
                    .readValue(PAYLOAD, SagaEvent.class);

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
