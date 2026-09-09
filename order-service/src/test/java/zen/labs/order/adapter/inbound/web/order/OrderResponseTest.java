/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@DisplayName("OrderResponse")
class OrderResponseTest {

    // ---------------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------------

    private static final String RESOURCE_ID = "order-001";
    private static final String CUSTOMER_ID = "customer-abc";
    private static final String SHIPPING_ADDRESS = "42 Elm Street, Springfield";
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private Order buildOrder(OrderStatus status, List<OrderItem> items) {
        return Order.builder()
                .resourceId(RESOURCE_ID)
                .customerId(CUSTOMER_ID)
                .status(status)
                .shippingAddress(SHIPPING_ADDRESS)
                .createdAt(CREATED_AT)
                .items(items)
                .build();
    }

    // ---------------------------------------------------------------------------------
    // from(Order) tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("from(Order)")
    class From {

        @Test
        @DisplayName("should map all scalar fields correctly")
        void shouldMapAllScalarFieldsCorrectly() {
            // Given
            Order order = buildOrder(OrderStatus.CONFIRMED, List.of());

            // When
            OrderResponse response = OrderResponse.from(order);

            // Then
            assertThat(response.resourceId()).isEqualTo(RESOURCE_ID);
            assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(response.shippingAddress()).isEqualTo(SHIPPING_ADDRESS);
            assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("should map status as the enum name string, not the enum itself")
        void shouldMapStatusAsEnumNameString() {
            // Given
            Order order = buildOrder(OrderStatus.CONFIRMED, List.of());

            // When
            OrderResponse response = OrderResponse.from(order);

            // Then
            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED.name());
        }

        @Test
        @DisplayName("should compute totalAmount correctly from order items")
        void shouldComputeTotalAmountCorrectly() {
            // Given — 2 x 15.00 + 1 x 10.00 = 40.00
            List<OrderItem> items = List.of(
                    new OrderItem("prod-A", 2, new BigDecimal("15.00")),
                    new OrderItem("prod-B", 1, new BigDecimal("10.00")));
            Order order = buildOrder(OrderStatus.PENDING, items);

            // When
            OrderResponse response = OrderResponse.from(order);

            // Then
            assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        }

        @Test
        @DisplayName("should map all item fields correctly from order items")
        void shouldMapItemFieldsCorrectly() {
            // Given
            List<OrderItem> items = List.of(
                    new OrderItem("prod-A", 3, new BigDecimal("7.50")),
                    new OrderItem("prod-B", 1, new BigDecimal("20.00")));
            Order order = buildOrder(OrderStatus.PENDING, items);

            // When
            OrderResponse response = OrderResponse.from(order);

            // Then
            assertThat(response.items()).hasSize(2);

            OrderResponse.Item firstItem = response.items().get(0);
            assertThat(firstItem.productId()).isEqualTo("prod-A");
            assertThat(firstItem.quantity()).isEqualTo(3);
            assertThat(firstItem.unitPrice()).isEqualByComparingTo(new BigDecimal("7.50"));

            OrderResponse.Item secondItem = response.items().get(1);
            assertThat(secondItem.productId()).isEqualTo("prod-B");
            assertThat(secondItem.quantity()).isEqualTo(1);
            assertThat(secondItem.unitPrice()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("should return empty items list and zero totalAmount when order has no items")
        void shouldReturnEmptyItemsAndZeroTotal_whenOrderHasNoItems() {
            // Given
            Order order = buildOrder(OrderStatus.PENDING, List.of());

            // When
            OrderResponse response = OrderResponse.from(order);

            // Then
            assertThat(response.items()).isEmpty();
            assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should reflect the correct status string for each OrderStatus variant")
        void shouldReflectCorrectStatusString_forEachOrderStatusVariant() {
            // Verify each status maps to its enum name
            for (OrderStatus status : OrderStatus.values()) {
                Order order = buildOrder(status, List.of());
                OrderResponse response = OrderResponse.from(order);
                assertThat(response.status())
                        .as("Expected status name for %s", status)
                        .isEqualTo(status.name());
            }
        }
    }
}
