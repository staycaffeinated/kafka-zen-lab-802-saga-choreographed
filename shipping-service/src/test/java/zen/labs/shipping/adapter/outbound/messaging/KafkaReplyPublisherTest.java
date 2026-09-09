package zen.labs.shipping.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

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
    private static final String SAGA_ID = "saga-001";
    private static final String ORDER_ID = "order-001";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaReplyPublisher publisher;
    private final JsonMapper jsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        publisher = new KafkaReplyPublisher(kafkaTemplate, jsonMapper, TOPIC);
    }

    @Nested
    @DisplayName("publish — success reply")
    class SuccessReply {

        @Test
        @DisplayName("should send to the configured topic with orderId as the key")
        void shouldSendToTopicWithOrderIdAsKey() {
            // when
            publisher.publish("SHIPMENT_CREATED", SAGA_ID, ORDER_ID, true, null);

            // then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID), payloadCaptor.capture());
        }

        @Test
        @DisplayName("should include replyType, sagaId, orderId and success=true in the payload")
        void shouldIncludeCorrectFieldsInSuccessPayload() {
            // when
            publisher.publish("SHIPMENT_CREATED", SAGA_ID, ORDER_ID, true, null);

            // then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            SagaReply reply = jsonMapper.readValue(payload, SagaReply.class);
            assertThat(reply.replyType()).isEqualTo("SHIPMENT_CREATED");
            assertThat(reply.sagaId()).isEqualTo(SAGA_ID);
            assertThat(reply.orderId()).isEqualTo(ORDER_ID);
            assertThat(reply.success()).isTrue();
            assertThat(reply.failureReason()).isNull();
        }
    }

    @Nested
    @DisplayName("publish — failure reply")
    class FailureReply {

        @Test
        @DisplayName("should include success=false and the failureReason in the payload")
        void shouldIncludeFailureReasonAndSuccessFalseInPayload() {
            // given
            String failureReason = "Insufficient stock at warehouse";

            // when
            publisher.publish("SHIPMENT_FAILED", SAGA_ID, ORDER_ID, false, failureReason);

            // then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            SagaReply reply = jsonMapper.readValue(payload, SagaReply.class);
            assertThat(reply.replyType()).isEqualTo("SHIPMENT_FAILED");
            assertThat(reply.success()).isFalse();
            assertThat(reply.failureReason()).isEqualTo(failureReason);
        }

        @Test
        @DisplayName("should still use orderId as the Kafka message key for failure replies")
        void shouldUseOrderIdAsKeyForFailureReply() {
            // when
            publisher.publish("SHIPMENT_FAILED", SAGA_ID, ORDER_ID, false, "some reason");

            // then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID), payloadCaptor.capture());
        }
    }

    @Nested
    @DisplayName("publish — serialization failure")
    class SerializationFailure {

        @Test
        @DisplayName("should throw ApplicationException containing the replyType when serialization fails")
        void shouldThrowApplicationExceptionWhenSerializationFails() {
            // given — use a JsonMapper mock that throws on writeValueAsString
            JsonMapper brokenMapper = new JsonMapper() {
                @Override
                public String writeValueAsString(Object value) {
                    throw new RuntimeException("Simulated serialization failure");
                }
            };
            KafkaReplyPublisher brokenPublisher = new KafkaReplyPublisher(kafkaTemplate, brokenMapper, TOPIC);

            // when / then
            assertThatThrownBy(() -> brokenPublisher.publish("SHIPMENT_CREATED", SAGA_ID, ORDER_ID, true, null))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("SHIPMENT_CREATED");
        }
    }
}
