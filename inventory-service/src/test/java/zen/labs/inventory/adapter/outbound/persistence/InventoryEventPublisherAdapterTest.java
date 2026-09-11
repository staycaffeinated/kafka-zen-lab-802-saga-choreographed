package zen.labs.inventory.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.inventory.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.inventory.adapter.outbound.persistence.repository.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class InventoryEventPublisherAdapterTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private static final String TOPIC = "kafka-zen.lab-802.saga.inventory.events";
    private static final String ORDER_ID = "order-001";
    private static final String MOCK_PAYLOAD = "{\"orderId\":\"order-001\"}";

    private InventoryEventPublisherAdapter publisher;

    @BeforeEach
    void setUp() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenReturn(MOCK_PAYLOAD);
        publisher = new InventoryEventPublisherAdapter(outboxRepository, jsonMapper, TOPIC);
    }

    private OutboxJpaEntity captureRow() {
        var captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("publishInventoryReserved")
    class PublishInventoryReserved {

        @Test
        @DisplayName("should save row with INVENTORY_RESERVED type and routing fields")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishInventoryReserved(ORDER_ID, BigDecimal.valueOf(50), "123 Main St");

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("INVENTORY_RESERVED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
            assertThat(row.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("publishInventoryReservationFailed")
    class PublishInventoryReservationFailed {

        @Test
        @DisplayName("should save row with INVENTORY_RESERVATION_FAILED type")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishInventoryReservationFailed(ORDER_ID, "Insufficient stock for product: p-1");

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("INVENTORY_RESERVATION_FAILED");
        }
    }

    @Nested
    @DisplayName("publishInventoryReleased")
    class PublishInventoryReleased {

        @Test
        @DisplayName("should save row with INVENTORY_RELEASED type")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishInventoryReleased(ORDER_ID);

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("INVENTORY_RELEASED");
        }
    }
}
