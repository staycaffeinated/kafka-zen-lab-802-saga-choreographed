/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.inbound.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import zen.labs.payment.application.port.inbound.HandlePaymentCommandUseCase;

@ExtendWith(MockitoExtension.class)
class SagaCommandListenerTest {

    @Mock
    private HandlePaymentCommandUseCase handlePaymentCommandUseCase;

    @Mock
    private Acknowledgment ack;

    private SagaCommandListener listener;

    private final JsonMapper realJsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        listener = new SagaCommandListener(handlePaymentCommandUseCase, realJsonMapper);
    }

    @Nested
    @DisplayName("onCommand — PROCESS_PAYMENT")
    class ProcessPaymentCommand {

        @Test
        @DisplayName("should call processPayment with correct sagaId, orderId and amount, then ack")
        @SuppressWarnings("java:S6068") // the eq() call is needed, otherwise the test will fail
        void shouldCallProcessPayment_withCorrectArgs() throws Exception {
            // Given
            SagaCommand cmd = new SagaCommand(
                    "saga-001", "order-001", "PROCESS_PAYMENT", List.of(), BigDecimal.valueOf(500), null);
            String payload = realJsonMapper.writeValueAsString(cmd);

            // When
            listener.onCommand(payload, ack);

            // Then
            verify(handlePaymentCommandUseCase)
                    .processPayment(eq("saga-001"), eq("order-001"), eq(BigDecimal.valueOf(500)));
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("onCommand — REFUND_PAYMENT")
    class RefundPaymentCommand {

        @Test
        @DisplayName("should call refundPayment with correct sagaId and orderId, then ack")
        void shouldCallRefundPayment_withCorrectArgs() throws Exception {
            // Given
            SagaCommand cmd = new SagaCommand("saga-002", "order-002", "REFUND_PAYMENT", List.of(), null, null);
            String payload = realJsonMapper.writeValueAsString(cmd);

            // When
            listener.onCommand(payload, ack);

            // Then
            verify(handlePaymentCommandUseCase).refundPayment("saga-002", "order-002");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("onCommand — unknown command type")
    class UnknownCommandType {

        @Test
        @DisplayName("should not interact with use case and still ack when command type is unknown")
        void shouldNotInteractWithUseCase_whenCommandTypeUnknown() throws Exception {
            // Given
            SagaCommand cmd = new SagaCommand("saga-003", "order-003", "UNKNOWN_COMMAND", List.of(), null, null);
            String payload = realJsonMapper.writeValueAsString(cmd);

            // When
            listener.onCommand(payload, ack);

            // Then
            verifyNoInteractions(handlePaymentCommandUseCase);
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("onCommand — malformed JSON")
    class MalformedJson {

        @Test
        @DisplayName("should not call use case and should still ack when payload is malformed JSON")
        void shouldNotCallUseCase_whenPayloadIsMalformed() {
            // Given — a JsonMapper mock that throws when readValue is called
            JsonMapper throwingMapper = mock(JsonMapper.class);
            when(throwingMapper.readValue(anyString(), eq(SagaCommand.class)))
                    .thenThrow(new RuntimeException("simulated parse failure"));

            SagaCommandListener listenerWithBadMapper =
                    new SagaCommandListener(handlePaymentCommandUseCase, throwingMapper);

            // When
            listenerWithBadMapper.onCommand("{not valid json}", ack);

            // Then
            verifyNoInteractions(handlePaymentCommandUseCase);
            verify(ack).acknowledge();
        }
    }
}
