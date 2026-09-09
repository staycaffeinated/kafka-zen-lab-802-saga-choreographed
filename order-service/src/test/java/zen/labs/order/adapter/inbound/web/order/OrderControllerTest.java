/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.handlers.GlobalExceptionHandler;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.inbound.FindOrderUseCase;
import zen.labs.order.application.port.inbound.ListOrdersUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, OrderControllerTest.TestConfig.class})
@ActiveProfiles("test")
@DisplayName("OrderController")
class OrderControllerTest {

    private static final String BASE_URL = "/api/v1/orders";

    /**
     * Provides beans required by GlobalExceptionHandler that are not loaded
     * by the @WebMvcTest slice automatically.
     */
    static class TestConfig {
        @Bean
        JsonMapper jsonMapper() {
            return new JsonMapper();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockitoBean
    private FindOrderUseCase findOrderUseCase;

    @MockitoBean
    private ListOrdersUseCase listOrdersUseCase;

    @MockitoBean
    private CancelOrderUseCase cancelOrderUseCase;

    @Nested
    @DisplayName("POST /api/v1/orders")
    class PlaceOrder {

        @Test
        @DisplayName("should return 201 and response body when request is valid")
        void shouldReturn201WithResponseBodyForValidRequest() throws Exception {
            // Given
            var requestBody = """
                    {
                        "customerId": "customer-001",
                        "items": [
                            {
                                "productId": "product-A",
                                "quantity": 2,
                                "unitPrice": 15.00
                            }
                        ],
                        "shippingAddress": "42 Elm Street, Springfield"
                    }
                    """;

            Order placedOrder = Order.builder()
                    .resourceId("order-abc-123")
                    .customerId("customer-001")
                    .status(OrderStatus.CONFIRMING)
                    .shippingAddress("42 Elm Street, Springfield")
                    .items(List.of(new OrderItem("product-A", 2, new BigDecimal("15.00"))))
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();

            when(placeOrderUseCase.placeOrder(any(PlaceOrderCommand.class))).thenReturn(placedOrder);

            // When / Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resourceId").value("order-abc-123"))
                    .andExpect(jsonPath("$.customerId").value("customer-001"))
                    .andExpect(jsonPath("$.status").value("CONFIRMING"))
                    .andExpect(jsonPath("$.shippingAddress").value("42 Elm Street, Springfield"))
                    .andExpect(jsonPath("$.totalAmount").value(30.00))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items[0].productId").value("product-A"));
        }

        @Test
        @DisplayName("should pass Idempotency-Key header to use case when provided")
        void shouldPassIdempotencyKeyToUseCase() throws Exception {
            // Given
            var requestBody = """
                    {
                        "customerId": "customer-001",
                        "items": [{"productId": "product-A", "quantity": 1, "unitPrice": 10.00}],
                        "shippingAddress": "42 Elm Street, Springfield"
                    }
                    """;
            var placedOrder = Order.builder()
                    .resourceId("order-abc-123")
                    .customerId("customer-001")
                    .status(OrderStatus.CONFIRMING)
                    .shippingAddress("42 Elm Street, Springfield")
                    .items(List.of(new OrderItem("product-A", 1, new BigDecimal("10.00"))))
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(placeOrderUseCase.placeOrder(any(PlaceOrderCommand.class))).thenReturn(placedOrder);

            // When
            mockMvc.perform(post(BASE_URL)
                            .header("Idempotency-Key", "my-unique-key-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            // Then
            var captor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
            verify(placeOrderUseCase).placeOrder(captor.capture());
            assertThat(captor.getValue().idempotencyKey()).isEqualTo("my-unique-key-123");
        }

        @Test
        @DisplayName("should pass null idempotency key when header is absent")
        void shouldPassNullIdempotencyKeyWhenHeaderAbsent() throws Exception {
            // Given
            var requestBody = """
                    {
                        "customerId": "customer-001",
                        "items": [{"productId": "product-A", "quantity": 1, "unitPrice": 10.00}],
                        "shippingAddress": "42 Elm Street, Springfield"
                    }
                    """;
            var placedOrder = Order.builder()
                    .resourceId("order-abc-123")
                    .customerId("customer-001")
                    .status(OrderStatus.CONFIRMING)
                    .shippingAddress("42 Elm Street, Springfield")
                    .items(List.of(new OrderItem("product-A", 1, new BigDecimal("10.00"))))
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(placeOrderUseCase.placeOrder(any(PlaceOrderCommand.class))).thenReturn(placedOrder);

            // When
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            // Then
            var captor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
            verify(placeOrderUseCase).placeOrder(captor.capture());
            assertThat(captor.getValue().idempotencyKey()).isNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPlaceOrderRequests")
        void shouldReturn400ForInvalidRequest(String description, String requestBody) throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        static Stream<Arguments> invalidPlaceOrderRequests() {
            return Stream.of(
                    Arguments.of("blank customerId", """
                            {"customerId":"","items":[{"productId":"product-A","quantity":1,"unitPrice":10.00}],"shippingAddress":"42 Elm Street"}
                            """),
                    Arguments.of("empty items list", """
                            {"customerId":"customer-001","items":[],"shippingAddress":"42 Elm Street"}
                            """),
                    Arguments.of("blank shippingAddress", """
                            {"customerId":"customer-001","items":[{"productId":"product-A","quantity":1,"unitPrice":10.00}],"shippingAddress":""}
                            """),
                    Arguments.of("zero item quantity", """
                            {"customerId":"customer-001","items":[{"productId":"product-A","quantity":0,"unitPrice":10.00}],"shippingAddress":"42 Elm Street"}
                            """),
                    Arguments.of("zero item unitPrice", """
                            {"customerId":"customer-001","items":[{"productId":"product-A","quantity":1,"unitPrice":0.00}],"shippingAddress":"42 Elm Street"}
                            """),
                    Arguments.of("blank item productId", """
                            {"customerId":"customer-001","items":[{"productId":"","quantity":1,"unitPrice":10.00}],"shippingAddress":"42 Elm Street"}
                            """));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{resourceId}")
    class GetOrder {

        @Test
        @DisplayName("should return 200 and response body when order is found")
        void shouldReturn200WithResponseBodyWhenOrderIsFound() throws Exception {
            // Given
            String resourceId = "order-xyz-456";

            Order foundOrder = Order.builder()
                    .resourceId(resourceId)
                    .customerId("customer-002")
                    .status(OrderStatus.CONFIRMED)
                    .shippingAddress("99 Oak Avenue, Portland")
                    .items(List.of(
                            new OrderItem("product-B", 3, new BigDecimal("20.00")),
                            new OrderItem("product-C", 1, new BigDecimal("50.00"))))
                    .createdAt(Instant.parse("2026-01-15T10:00:00Z"))
                    .build();

            when(findOrderUseCase.findOrder(resourceId)).thenReturn(foundOrder);

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{resourceId}", resourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceId").value(resourceId))
                    .andExpect(jsonPath("$.customerId").value("customer-002"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.shippingAddress").value("99 Oak Avenue, Portland"))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(2));
        }

        @Test
        @DisplayName("should return 404 when order is not found")
        void shouldReturn404WhenOrderIsNotFound() throws Exception {
            // Given
            String resourceId = "order-unknown-999";

            when(findOrderUseCase.findOrder(resourceId))
                    .thenThrow(new ResourceNotFoundException("Order not found: " + resourceId));

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{resourceId}", resourceId)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders?customerId=...")
    class ListOrders {

        @Test
        @DisplayName("should return 200 with order list when customer has orders")
        void shouldReturn200WithOrderList() throws Exception {
            // Given
            String customerId = "customer-001";
            List<Order> orders = List.of(
                    Order.builder()
                            .resourceId("order-aaa")
                            .customerId(customerId)
                            .status(OrderStatus.CONFIRMED)
                            .shippingAddress("42 Elm Street")
                            .items(List.of(new OrderItem("product-A", 2, new BigDecimal("15.00"))))
                            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                            .build(),
                    Order.builder()
                            .resourceId("order-bbb")
                            .customerId(customerId)
                            .status(OrderStatus.CONFIRMING)
                            .shippingAddress("99 Oak Avenue")
                            .items(List.of())
                            .createdAt(Instant.parse("2026-01-02T00:00:00Z"))
                            .build());

            when(listOrdersUseCase.listOrdersByCustomer(customerId)).thenReturn(orders);

            // When / Then
            mockMvc.perform(get(BASE_URL).param("customerId", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].resourceId").value("order-aaa"))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$[1].resourceId").value("order-bbb"))
                    .andExpect(jsonPath("$[1].status").value("CONFIRMING"));
        }

        @Test
        @DisplayName("should return 200 with empty array when customer has no orders")
        void shouldReturn200WithEmptyArray_whenNoOrders() throws Exception {
            // Given
            String customerId = "customer-new";
            when(listOrdersUseCase.listOrdersByCustomer(customerId)).thenReturn(List.of());

            // When / Then
            mockMvc.perform(get(BASE_URL).param("customerId", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 400 when customerId parameter is missing")
        void shouldReturn400WhenCustomerIdMissing() throws Exception {
            mockMvc.perform(get(BASE_URL)).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/orders/{resourceId}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("should return 204 when cancellation is accepted")
        void shouldReturn204WhenCancellationAccepted() throws Exception {
            String resourceId = "order-abc-123";
            doNothing().when(cancelOrderUseCase).cancelOrder(resourceId);

            mockMvc.perform(post(BASE_URL + "/{resourceId}/cancel", resourceId)).andExpect(status().isNoContent());

            verify(cancelOrderUseCase).cancelOrder(resourceId);
        }

        @Test
        @DisplayName("should return 404 when order is not found")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            String resourceId = "order-unknown-999";
            doThrow(new ResourceNotFoundException("Order not found: " + resourceId))
                    .when(cancelOrderUseCase)
                    .cancelOrder(resourceId);

            mockMvc.perform(post(BASE_URL + "/{resourceId}/cancel", resourceId)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 422 when order is not in a cancellable state")
        void shouldReturn422WhenNotCancellable() throws Exception {
            String resourceId = "order-confirmed-001";
            doThrow(new UnprocessableRequestException("Order cannot be cancelled in status: CONFIRMED"))
                    .when(cancelOrderUseCase)
                    .cancelOrder(resourceId);

            mockMvc.perform(post(BASE_URL + "/{resourceId}/cancel", resourceId))
                    .andExpect(status().isUnprocessableContent());
        }
    }
}
