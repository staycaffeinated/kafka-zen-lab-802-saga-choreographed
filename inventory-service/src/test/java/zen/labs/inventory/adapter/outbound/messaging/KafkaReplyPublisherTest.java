/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.application.common.exceptions.ApplicationException;

@ExtendWith(MockitoExtension.class)
class KafkaReplyPublisherTest {

    private static final String TEST_TOPIC = "test-replies-topic";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaReplyPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaReplyPublisher(kafkaTemplate, new JsonMapper(), TEST_TOPIC);
    }

    @Nested
    @DisplayName("publish — success reply")
    class PublishSuccessReply {

        @Test
        @DisplayName("should send to correct topic with orderId as key and payload containing replyType and sagaId")
        void shouldSendToCorrectTopicWithCorrectKeyAndPayload() {
            // Given
            String replyType = "INVENTORY_RESERVED";
            String sagaId = "saga-001";
            String orderId = "order-001";

            // When
            publisher.publish(replyType, sagaId, orderId, true, null);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TEST_TOPIC), eq(orderId), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertThat(payload)
                    .contains("INVENTORY_RESERVED")
                    .contains("saga-001")
                    .contains("order-001")
                    .contains("\"success\":true");
        }
    }

    @Nested
    @DisplayName("publish — failure reply")
    class PublishFailureReply {

        @Test
        @DisplayName("should include failureReason and success=false in payload")
        void shouldIncludeFailureReasonAndSuccessFalseInPayload() {
            // Given
            String replyType = "INVENTORY_RESERVATION_FAILED";
            String sagaId = "saga-002";
            String orderId = "order-002";
            String failureReason = "Insufficient stock for product: product-X";

            // When
            publisher.publish(replyType, sagaId, orderId, false, failureReason);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TEST_TOPIC), eq(orderId), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertThat(payload)
                    .contains("INVENTORY_RESERVATION_FAILED")
                    .contains("\"success\":false")
                    .contains("Insufficient stock for product: product-X");
        }
    }

    @Nested
    @DisplayName("publish — serialization failure")
    class SerializationFailure {

        @Test
        @DisplayName("should throw ApplicationException with replyType in message when JsonMapper throws")
        void shouldThrowApplicationException_whenSerializationFails() {
            // Given
            JsonMapper throwingMapper = mock(JsonMapper.class);
            when(throwingMapper.writeValueAsString(anyString()))
                    .thenThrow(new RuntimeException("simulated serialization failure"));

            KafkaReplyPublisher failingPublisher = new KafkaReplyPublisher(kafkaTemplate, throwingMapper, TEST_TOPIC);

            // When / Then
            assertThatThrownBy(
                            () -> failingPublisher.publish("INVENTORY_RESERVED", "saga-003", "order-003", true, null))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("INVENTORY_RESERVED");
        }
    }
}
