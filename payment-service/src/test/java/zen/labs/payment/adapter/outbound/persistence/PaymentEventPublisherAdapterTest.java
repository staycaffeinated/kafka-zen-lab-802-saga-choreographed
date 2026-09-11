package zen.labs.payment.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.payment.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.payment.adapter.outbound.persistence.repository.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherAdapterTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private static final String TOPIC = "kafka-zen.lab-802.saga.payment.events";
    private static final String ORDER_ID = "order-001";
    private static final String MOCK_PAYLOAD = "{\"orderId\":\"order-001\"}";

    private PaymentEventPublisherAdapter publisher;

    @BeforeEach
    void setUp() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenReturn(MOCK_PAYLOAD);
        publisher = new PaymentEventPublisherAdapter(outboxRepository, jsonMapper, TOPIC);
    }

    private OutboxJpaEntity captureRow() {
        var captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("publishPaymentProcessed")
    class PublishPaymentProcessed {

        @Test
        @DisplayName("should save row with PAYMENT_PROCESSED type and routing fields")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishPaymentProcessed(ORDER_ID, "123 Main St");

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("PAYMENT_PROCESSED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
        }
    }

    @Nested
    @DisplayName("publishPaymentFailed")
    class PublishPaymentFailed {

        @Test
        @DisplayName("should save row with PAYMENT_FAILED type")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishPaymentFailed(ORDER_ID, "Amount exceeds maximum allowed: 99999");

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("PAYMENT_FAILED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
        }
    }

    @Nested
    @DisplayName("publishPaymentRefunded")
    class PublishPaymentRefunded {

        @Test
        @DisplayName("should save row with PAYMENT_REFUNDED type")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishPaymentRefunded(ORDER_ID);

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("PAYMENT_REFUNDED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
        }
    }
}
