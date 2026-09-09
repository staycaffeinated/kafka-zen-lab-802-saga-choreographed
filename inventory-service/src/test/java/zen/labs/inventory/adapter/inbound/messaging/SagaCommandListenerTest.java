/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.inbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.inventory.application.port.inbound.HandleInventoryCommandUseCase;
import zen.labs.inventory.application.port.inbound.HandleInventoryCommandUseCase.Item;

@ExtendWith(MockitoExtension.class)
class SagaCommandListenerTest {

    @Mock
    private HandleInventoryCommandUseCase handleInventoryCommandUseCase;

    @Mock
    private Acknowledgment ack;

    private SagaCommandListener listener;

    private final JsonMapper realJsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        listener = new SagaCommandListener(handleInventoryCommandUseCase, realJsonMapper);
    }

    @Nested
    @DisplayName("onCommand — RESERVE_INVENTORY")
    class ReserveInventoryCommand {

        @Test
        @DisplayName("should call reserveInventory with correct sagaId, orderId and mapped items, then ack")
        @SuppressWarnings({"unchecked", "java:S6068" // the eq() call is needed, otherwise the test will fail
        })
        void shouldCallReserveInventory_withCorrectArgs() throws Exception {
            // Given
            SagaCommand cmd = new SagaCommand(
                    "saga-001",
                    "order-001",
                    "RESERVE_INVENTORY",
                    List.of(new SagaCommandItem("product-A", 5), new SagaCommandItem("product-B", 20)),
                    null,
                    null);
            String payload = realJsonMapper.writeValueAsString(cmd);

            // When
            listener.onCommand(payload, ack);

            // Then
            ArgumentCaptor<List<Item>> itemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(handleInventoryCommandUseCase)
                    .reserveInventory(eq("saga-001"), eq("order-001"), itemsCaptor.capture());

            List<Item> capturedItems = itemsCaptor.getValue();
            assertThat(capturedItems).hasSize(2);
            assertThat(capturedItems.get(0).productId()).isEqualTo("product-A");
            assertThat(capturedItems.get(0).quantity()).isEqualTo(5);
            assertThat(capturedItems.get(1).productId()).isEqualTo("product-B");
            assertThat(capturedItems.get(1).quantity()).isEqualTo(20);

            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("onCommand — RELEASE_INVENTORY")
    class ReleaseInventoryCommand {

        @Test
        @DisplayName("should call releaseInventory with correct sagaId and orderId, then ack")
        void shouldCallReleaseInventory_withCorrectArgs() throws Exception {
            // Given
            SagaCommand cmd = new SagaCommand("saga-002", "order-002", "RELEASE_INVENTORY", List.of(), null, null);
            String payload = realJsonMapper.writeValueAsString(cmd);

            // When
            listener.onCommand(payload, ack);

            // Then
            verify(handleInventoryCommandUseCase).releaseInventory("saga-002", "order-002");
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
            verifyNoInteractions(handleInventoryCommandUseCase);
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
                    new SagaCommandListener(handleInventoryCommandUseCase, throwingMapper);

            // When
            listenerWithBadMapper.onCommand("{not valid json}", ack);

            // Then
            verifyNoInteractions(handleInventoryCommandUseCase);
            verify(ack).acknowledge();
        }
    }
}
