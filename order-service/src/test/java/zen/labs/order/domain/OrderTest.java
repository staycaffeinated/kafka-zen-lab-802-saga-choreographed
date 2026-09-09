/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

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

@DisplayName("Order")
class OrderTest {

    // ---------------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------------

    private static final String RESOURCE_ID = "order-001";
    private static final String CUSTOMER_ID = "customer-abc";
    private static final String SHIPPING_ADDRESS = "42 Elm Street, Springfield";
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private Order buildOrder(List<OrderItem> items) {
        return Order.builder()
                .resourceId(RESOURCE_ID)
                .customerId(CUSTOMER_ID)
                .status(OrderStatus.PENDING)
                .shippingAddress(SHIPPING_ADDRESS)
                .createdAt(CREATED_AT)
                .items(items)
                .build();
    }

    // ---------------------------------------------------------------------------------
    // Builder tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should create an Order with all fields set correctly")
        void shouldCreateOrderWithAllFieldsSetCorrectly() {
            // Given
            List<OrderItem> items = List.of(new OrderItem("prod-1", 1, new BigDecimal("10.00")));

            // When
            Order order = buildOrder(items);

            // Then
            assertThat(order.getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getShippingAddress()).isEqualTo(SHIPPING_ADDRESS);
            assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(order.getItems()).hasSize(1);
        }
    }

    // ---------------------------------------------------------------------------------
    // totalAmount() tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("totalAmount")
    class TotalAmount {

        @Test
        @DisplayName("should return BigDecimal.ZERO when items list is empty")
        void shouldReturnZero_whenItemsListIsEmpty() {
            // Given
            Order order = buildOrder(List.of());

            // When
            BigDecimal total = order.totalAmount();

            // Then
            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("singleItemTotalAmountCases")
        void shouldCalculateTotalForSingleItem(
                String description, int quantity, String unitPrice, String expectedTotal) {
            OrderItem item = new OrderItem("prod-1", quantity, new BigDecimal(unitPrice));
            assertThat(buildOrder(List.of(item)).totalAmount()).isEqualByComparingTo(new BigDecimal(expectedTotal));
        }

        static Stream<Arguments> singleItemTotalAmountCases() {
            return Stream.of(
                    Arguments.of("single item with quantity 1", 1, "25.50", "25.50"),
                    Arguments.of("multiplies unit price by quantity > 1", 3, "10.00", "30.00"),
                    Arguments.of("handles fractional unit prices", 4, "2.49", "9.96"));
        }

        @Test
        @DisplayName("should sum amounts from all items when multiple items are present")
        void shouldSumAmountsFromAllItems_whenMultipleItemsArePresent() {
            // Given — two items: 2 x 10.00 = 20.00 and 3 x 5.00 = 15.00 => 35.00
            List<OrderItem> items = List.of(
                    new OrderItem("prod-1", 2, new BigDecimal("10.00")),
                    new OrderItem("prod-2", 3, new BigDecimal("5.00")));
            Order order = buildOrder(items);

            // When
            BigDecimal total = order.totalAmount();

            // Then
            assertThat(total).isEqualByComparingTo(new BigDecimal("35.00"));
        }
    }

    // ---------------------------------------------------------------------------------
    // withStatus() tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("withStatus")
    class WithStatus {

        @Test
        @DisplayName("should return a new Order instance with the updated status")
        void shouldReturnNewOrderWithUpdatedStatus() {
            // Given
            Order original = buildOrder(List.of());

            // When
            Order updated = original.withStatus(OrderStatus.CONFIRMED);

            // Then
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("should not mutate the original Order instance")
        void shouldNotMutateOriginalOrder() {
            // Given
            Order original = buildOrder(List.of());

            // When
            original.withStatus(OrderStatus.CONFIRMED);

            // Then
            assertThat(original.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("should preserve all other fields when only status is changed")
        void shouldPreserveAllOtherFields_whenStatusIsChanged() {
            // Given
            List<OrderItem> items = List.of(new OrderItem("prod-1", 1, new BigDecimal("9.99")));
            Order original = buildOrder(items);

            // When
            Order updated = original.withStatus(OrderStatus.CANCELLING);

            // Then
            assertThat(updated.getResourceId()).isEqualTo(original.getResourceId());
            assertThat(updated.getCustomerId()).isEqualTo(original.getCustomerId());
            assertThat(updated.getShippingAddress()).isEqualTo(original.getShippingAddress());
            assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
            assertThat(updated.getItems()).isEqualTo(original.getItems());
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        }

        @Test
        @DisplayName("should support all OrderStatus transitions")
        void shouldSupportAllOrderStatusTransitions() {
            // Given
            Order order = buildOrder(List.of());

            // When / Then — verify each status can be applied
            assertThat(order.withStatus(OrderStatus.PENDING).getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.withStatus(OrderStatus.CONFIRMING).getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            assertThat(order.withStatus(OrderStatus.CONFIRMED).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.withStatus(OrderStatus.CANCELLING).getStatus()).isEqualTo(OrderStatus.CANCELLING);
            assertThat(order.withStatus(OrderStatus.CANCELLED).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
