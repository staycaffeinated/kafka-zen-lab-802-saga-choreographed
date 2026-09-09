/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService")
class OrderQueryServiceTest {

    @Mock
    private OrderDataStore orderDataStore;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Nested
    @DisplayName("findOrder")
    class FindOrder {

        @Test
        @DisplayName("should return the Order when it exists for the given resourceId")
        void shouldReturnOrder_whenResourceIdExists() {
            // Given
            String resourceId = "order-abc-123";
            Order expectedOrder = Order.builder()
                    .resourceId(resourceId)
                    .customerId("customer-xyz-456")
                    .status(OrderStatus.PENDING)
                    .shippingAddress("123 Main St, Anytown, USA")
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .items(List.of())
                    .build();

            when(orderDataStore.findByResourceId(resourceId)).thenReturn(Optional.of(expectedOrder));

            // When
            Order result = orderQueryService.findOrder(resourceId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getResourceId()).isEqualTo(resourceId);
            assertThat(result.getCustomerId()).isEqualTo("customer-xyz-456");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderDataStore).findByResourceId(resourceId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no Order exists for the given resourceId")
        void shouldThrowResourceNotFoundException_whenResourceIdDoesNotExist() {
            // Given
            String resourceId = "order-nonexistent-999";
            when(orderDataStore.findByResourceId(resourceId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orderQueryService.findOrder(resourceId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(resourceId);

            verify(orderDataStore).findByResourceId(resourceId);
        }
    }

    @Nested
    @DisplayName("listOrdersByCustomer")
    class ListOrdersByCustomer {

        @Test
        @DisplayName("should return list of orders for the given customerId")
        void shouldReturnOrders_whenCustomerHasOrders() {
            // Given
            String customerId = "customer-xyz-456";
            List<Order> orders = List.of(
                    Order.builder()
                            .resourceId("order-001")
                            .customerId(customerId)
                            .status(OrderStatus.CONFIRMED)
                            .shippingAddress("42 Elm St")
                            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                            .items(List.of(new OrderItem("prod-A", 1, new BigDecimal("10.00"))))
                            .build(),
                    Order.builder()
                            .resourceId("order-002")
                            .customerId(customerId)
                            .status(OrderStatus.CONFIRMING)
                            .shippingAddress("99 Oak Ave")
                            .createdAt(Instant.parse("2026-01-02T00:00:00Z"))
                            .items(List.of())
                            .build());

            when(orderDataStore.findByCustomerId(customerId)).thenReturn(orders);

            // When
            List<Order> result = orderQueryService.listOrdersByCustomer(customerId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getResourceId()).isEqualTo("order-001");
            assertThat(result.get(1).getResourceId()).isEqualTo("order-002");
            verify(orderDataStore).findByCustomerId(customerId);
        }

        @Test
        @DisplayName("should return empty list when customer has no orders")
        void shouldReturnEmptyList_whenCustomerHasNoOrders() {
            // Given
            String customerId = "customer-new-000";
            when(orderDataStore.findByCustomerId(customerId)).thenReturn(List.of());

            // When
            List<Order> result = orderQueryService.listOrdersByCustomer(customerId);

            // Then
            assertThat(result).isEmpty();
            verify(orderDataStore).findByCustomerId(customerId);
        }
    }
}
