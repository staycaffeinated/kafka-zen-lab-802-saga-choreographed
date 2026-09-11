/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.outbound.persistence;

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
import zen.labs.shipping.adapter.outbound.persistence.entity.OutboxJpaEntity;
import zen.labs.shipping.adapter.outbound.persistence.repository.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class ShippingEventPublisherAdapterTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private static final String TOPIC = "kafka-zen.lab-802.saga.shipping.events";
    private static final String ORDER_ID = "order-001";
    private static final String MOCK_PAYLOAD = "{\"orderId\":\"order-001\"}";

    private ShippingEventPublisherAdapter publisher;

    @BeforeEach
    void setUp() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenReturn(MOCK_PAYLOAD);
        publisher = new ShippingEventPublisherAdapter(outboxRepository, jsonMapper, TOPIC);
    }

    private OutboxJpaEntity captureRow() {
        var captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("publishShipmentCreated")
    class PublishShipmentCreated {

        @Test
        @DisplayName("should save row with SHIPMENT_CREATED type and routing fields")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishShipmentCreated(ORDER_ID);

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("SHIPMENT_CREATED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
        }
    }

    @Nested
    @DisplayName("publishShipmentFailed")
    class PublishShipmentFailed {

        @Test
        @DisplayName("should save row with SHIPMENT_FAILED type")
        void shouldSaveCorrectOutboxRow() {
            publisher.publishShipmentFailed(ORDER_ID, "Shipping address is required");

            var row = captureRow();
            assertThat(row.getAggregateType()).isEqualTo(TOPIC);
            assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.getType()).isEqualTo("SHIPMENT_FAILED");
            assertThat(row.getPayload()).isEqualTo(MOCK_PAYLOAD);
        }
    }
}
