/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.order.domain.OrderItem;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaCommandPublisher")
class KafkaCommandPublisherTest {

    private static final String PAYMENT_COMMANDS_TOPIC = "payment-commands";
    private static final String INVENTORY_COMMANDS_TOPIC = "inventory-commands";
    private static final String SHIPPING_COMMANDS_TOPIC = "shipping-commands";

    private static final String SAGA_ID = "saga-001";
    private static final String ORDER_ID = "order-001";

    @Mock
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate;

    private JsonMapper jsonMapper;

    private KafkaCommandPublisher publisher;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        publisher = new KafkaCommandPublisher(
                kafkaTemplate, jsonMapper, PAYMENT_COMMANDS_TOPIC, INVENTORY_COMMANDS_TOPIC, SHIPPING_COMMANDS_TOPIC);
    }

    @Nested
    @DisplayName("reserveInventory")
    class ReserveInventory {

        @Test
        @DisplayName("should publish RESERVE_INVENTORY command to inventory-commands topic")
        void shouldPublishReserveInventoryCommand() {
            // Given
            List<OrderItem> items = List.of(
                    new OrderItem("product-A", 2, new BigDecimal("10.00")),
                    new OrderItem("product-B", 1, new BigDecimal("25.50")));

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            // When
            publisher.reserveInventory(SAGA_ID, ORDER_ID, items);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(INVENTORY_COMMANDS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(ORDER_ID);

            String payload = payloadCaptor.getValue();
            assertThat(payload)
                    .contains("RESERVE_INVENTORY")
                    .contains(SAGA_ID)
                    .contains(ORDER_ID)
                    .contains("product-A")
                    .contains("product-B");
        }

        @Test
        @DisplayName("should map OrderItem fields to SagaCommandItem correctly")
        void shouldMapOrderItemFieldsToCommandItems() {
            // Given
            List<OrderItem> items = List.of(new OrderItem("prod-XYZ", 5, new BigDecimal("9.99")));

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            // When
            publisher.reserveInventory(SAGA_ID, ORDER_ID, items);

            // Then
            verify(kafkaTemplate)
                    .send(
                            org.mockito.ArgumentMatchers.eq(INVENTORY_COMMANDS_TOPIC),
                            org.mockito.ArgumentMatchers.eq(ORDER_ID),
                            payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertThat(payload).contains("prod-XYZ").contains("5");
        }
    }

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("should publish PROCESS_PAYMENT command to payment-commands topic")
        void shouldPublishProcessPaymentCommand() {
            // Given
            BigDecimal amount = new BigDecimal("150.00");

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            // When
            publisher.processPayment(SAGA_ID, ORDER_ID, amount);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(PAYMENT_COMMANDS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(ORDER_ID);

            String payload = payloadCaptor.getValue();
            assertThat(payload)
                    .contains("PROCESS_PAYMENT")
                    .contains(SAGA_ID)
                    .contains(ORDER_ID)
                    .contains("150.00");
        }
    }

    @Nested
    @DisplayName("createShipment")
    class CreateShipment {

        @Test
        @DisplayName("should publish CREATE_SHIPMENT command to shipping-commands topic")
        void shouldPublishCreateShipmentCommand() {
            // Given
            String shippingAddress = "123 Main St, Springfield, USA";

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            // When
            publisher.createShipment(SAGA_ID, ORDER_ID, shippingAddress);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(SHIPPING_COMMANDS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(ORDER_ID);

            String payload = payloadCaptor.getValue();
            assertThat(payload)
                    .contains("CREATE_SHIPMENT")
                    .contains(SAGA_ID)
                    .contains(ORDER_ID)
                    .contains("123 Main St");
        }
    }

    @Nested
    @DisplayName("releaseInventory")
    class ReleaseInventory {

        @Test
        @DisplayName("should publish RELEASE_INVENTORY command to inventory-commands topic")
        void shouldPublishReleaseInventoryCommand() {
            // Given
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            // When
            publisher.releaseInventory(SAGA_ID, ORDER_ID);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(INVENTORY_COMMANDS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(ORDER_ID);

            String payload = payloadCaptor.getValue();
            assertThat(payload).contains("RELEASE_INVENTORY").contains(SAGA_ID).contains(ORDER_ID);
        }
    }

    @Nested
    @DisplayName("refundPayment")
    class RefundPayment {

        @Test
        @DisplayName("should publish REFUND_PAYMENT command to payment-commands topic")
        void shouldPublishRefundPaymentCommand() {
            // Given
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            // When
            publisher.refundPayment(SAGA_ID, ORDER_ID);

            // Then
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(PAYMENT_COMMANDS_TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo(ORDER_ID);

            String payload = payloadCaptor.getValue();
            assertThat(payload).contains("REFUND_PAYMENT").contains(SAGA_ID).contains(ORDER_ID);
        }
    }

    @Nested
    @DisplayName("publish error handling")
    class PublishErrorHandling {

        @Test
        @DisplayName("should throw ApplicationException when JSON serialization fails")
        void shouldThrowApplicationExceptionWhenSerializationFails() throws JacksonException {
            // Given
            JsonMapper brokenMapper = mock(JsonMapper.class);
            when(brokenMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new RuntimeException("Simulated serialization error"));

            KafkaCommandPublisher publisherWithBrokenMapper = new KafkaCommandPublisher(
                    kafkaTemplate,
                    brokenMapper,
                    PAYMENT_COMMANDS_TOPIC,
                    INVENTORY_COMMANDS_TOPIC,
                    SHIPPING_COMMANDS_TOPIC);

            // When / Then
            assertThatThrownBy(() -> publisherWithBrokenMapper.refundPayment(SAGA_ID, ORDER_ID))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("REFUND_PAYMENT");
        }

        @Test
        @DisplayName("should include the commandType in the ApplicationException message on failure")
        void shouldIncludeCommandTypeInExceptionMessageOnFailure() throws JacksonException {
            // Given
            JsonMapper brokenMapper = mock(JsonMapper.class);
            when(brokenMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new RuntimeException("Simulated serialization error"));

            KafkaCommandPublisher publisherWithBrokenMapper = new KafkaCommandPublisher(
                    kafkaTemplate,
                    brokenMapper,
                    PAYMENT_COMMANDS_TOPIC,
                    INVENTORY_COMMANDS_TOPIC,
                    SHIPPING_COMMANDS_TOPIC);

            // When / Then
            assertThatThrownBy(() -> publisherWithBrokenMapper.processPayment(SAGA_ID, ORDER_ID, BigDecimal.TEN))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("PROCESS_PAYMENT");
        }
    }
}
