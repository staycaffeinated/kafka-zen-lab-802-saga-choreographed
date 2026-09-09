/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.application.common.exceptions.ApplicationException;

@ExtendWith(MockitoExtension.class)
class KafkaReplyPublisherTest {

    private static final String TOPIC = "test-replies-topic";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaReplyPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaReplyPublisher(kafkaTemplate, new JsonMapper(), TOPIC);
    }

    @Nested
    @DisplayName("publish — success reply")
    class SuccessReply {

        @Test
        @DisplayName("should send to correct topic with orderId as key and payload containing replyType and sagaId")
        void shouldSendSuccessReply_withCorrectTopicKeyAndPayload() {
            // Given
            String sagaId = "saga-001";
            String orderId = "order-001";
            String replyType = "PAYMENT_PROCESSED";

            // When
            publisher.publish(replyType, sagaId, orderId, true, null);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq(orderId), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertThat(payload).contains("PAYMENT_PROCESSED").contains(sagaId).contains("\"success\":true");
        }
    }

    @Nested
    @DisplayName("publish — failure reply")
    class FailureReply {

        @Test
        @DisplayName("should include failureReason and success=false in payload for PAYMENT_FAILED")
        void shouldSendFailureReply_withFailureReasonAndSuccessFalse() {
            // Given
            String sagaId = "saga-002";
            String orderId = "order-002";
            String replyType = "PAYMENT_FAILED";
            String failureReason = "Amount exceeds maximum allowed: 10000.01";

            // When
            publisher.publish(replyType, sagaId, orderId, false, failureReason);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq(orderId), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertThat(payload)
                    .contains("PAYMENT_FAILED")
                    .contains("\"success\":false")
                    .contains("Amount exceeds maximum allowed: 10000.01");
        }
    }

    @Nested
    @DisplayName("publish — serialization failure")
    class SerializationFailure {

        @Test
        @DisplayName("should throw ApplicationException containing replyType when JsonMapper throws")
        void shouldThrowApplicationException_whenSerializationFails() {
            // Given — a JsonMapper mock that throws on writeValueAsString
            JsonMapper throwingMapper = org.mockito.Mockito.mock(JsonMapper.class);
            when(throwingMapper.writeValueAsString(anyString()))
                    .thenThrow(new RuntimeException("simulated serialization failure"));

            KafkaReplyPublisher failingPublisher = new KafkaReplyPublisher(kafkaTemplate, throwingMapper, TOPIC);

            // When / Then
            assertThatThrownBy(() -> failingPublisher.publish("PAYMENT_PROCESSED", "saga-003", "order-003", true, null))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("PAYMENT_PROCESSED");
        }
    }
}
