package zen.labs.order.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.order.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.domain.OrderItem;

@ExtendWith(MockitoExtension.class)
class OutboxOrderEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private static final String TOPIC = "kafka-zen.lab-802.saga.order.events";
    private static final String ORDER_ID = "order-001";
    private static final String MOCK_PAYLOAD = "{\"orderId\":\"order-001\"}";

    private OutboxOrderEventPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenReturn(MOCK_PAYLOAD);
        publisher = new OutboxOrderEventPublisher(outboxRepository, jsonMapper, TOPIC);
    }

    private OutboxJpaEntity captureRow() {
        var captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("publishOrderPlaced")
    class PublishOrderPlaced {

        @Test
        @DisplayName("should save row with ORDER_PLACED type and routing fields")
        void shouldSaveCorrectOutboxRow() {
            var items = List.of(new OrderItem("product-1", 2, BigDecimal.valueOf(25)));

            publisher.publishOrderPlaced(ORDER_ID, items, BigDecimal.valueOf(50), "123 Main St");

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("ORDER_PLACED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
            assertThat(row.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("publishCancellationRequested")
    class PublishCancellationRequested {

        @Test
        @DisplayName("should save row with ORDER_CANCELLATION_REQUESTED type")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishCancellationRequested(ORDER_ID);

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("ORDER_CANCELLATION_REQUESTED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
            assertThat(row.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("persist — exception handling")
    class PersistExceptionHandling {

        @Test
        @DisplayName("should wrap serialization exception in ApplicationException")
        void shouldWrapJsonExceptionInApplicationException() throws Exception {
            when(jsonMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialization failed"));

            assertThatThrownBy(() -> publisher.publishCancellationRequested(ORDER_ID))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("Failed to persist outbox event");
        }
    }
}
