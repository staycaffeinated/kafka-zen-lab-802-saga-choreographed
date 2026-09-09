/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.order.adapter.outbound.persistence.entity.OrderItemJpaEntity;
import zen.labs.order.adapter.outbound.persistence.entity.OrderJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.OrderRepository;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderJpaDataStore")
class OrderJpaDataStoreTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderJpaDataStore orderJpaDataStore;

    private static final String RESOURCE_ID = "order-abc-123";
    private static final String CUSTOMER_ID = "customer-xyz-456";
    private static final String SHIPPING_ADDRESS = "123 Main St, Anytown, USA";
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private OrderJpaEntity buildOrderEntity() {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setResourceId(RESOURCE_ID);
        entity.setCustomerId(CUSTOMER_ID);
        entity.setStatus(OrderStatus.PENDING);
        entity.setShippingAddress(SHIPPING_ADDRESS);
        entity.setCreatedAt(CREATED_AT);

        OrderItemJpaEntity itemEntity = new OrderItemJpaEntity();
        itemEntity.setOrder(entity);
        itemEntity.setProductId("prod-001");
        itemEntity.setQuantity(2);
        itemEntity.setUnitPrice(new BigDecimal("19.99"));

        entity.setItems(new ArrayList<>(List.of(itemEntity)));
        return entity;
    }

    private Order buildOrderDomain() {
        return Order.builder()
                .resourceId(RESOURCE_ID)
                .customerId(CUSTOMER_ID)
                .status(OrderStatus.PENDING)
                .shippingAddress(SHIPPING_ADDRESS)
                .createdAt(CREATED_AT)
                .items(List.of(new OrderItem("prod-001", 2, new BigDecimal("19.99"))))
                .build();
    }

    @Nested
    @DisplayName("findByResourceId")
    class FindByResourceId {

        @Test
        @DisplayName("should return mapped domain Order when entity is found")
        void shouldReturnMappedDomain_whenEntityFound() {
            // Given
            OrderJpaEntity entity = buildOrderEntity();
            when(orderRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.of(entity));

            // When
            Optional<Order> result = orderJpaDataStore.findByResourceId(RESOURCE_ID);

            // Then
            assertThat(result).isPresent();
            Order order = result.get();
            assertThat(order.getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getShippingAddress()).isEqualTo(SHIPPING_ADDRESS);
            assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getItems().get(0).productId()).isEqualTo("prod-001");
            assertThat(order.getItems().get(0).quantity()).isEqualTo(2);
            assertThat(order.getItems().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("19.99"));

            verify(orderRepository).findByResourceId(RESOURCE_ID);
        }

        @Test
        @DisplayName("should return empty Optional when entity is not found")
        void shouldReturnEmptyOptional_whenEntityNotFound() {
            // Given
            when(orderRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.empty());

            // When
            Optional<Order> result = orderJpaDataStore.findByResourceId(RESOURCE_ID);

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findByResourceId(RESOURCE_ID);
        }

        @ParameterizedTest
        @NullSource
        void shouldThrowException_whenIdsNull(String resourceId) {
            assertThatThrownBy(() -> orderJpaDataStore.findByResourceId(resourceId))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("findByCustomerId")
    class FindByCustomerId {

        @Test
        @DisplayName("should return mapped domain orders for the given customerId")
        void shouldReturnMappedOrders_whenCustomerHasOrders() {
            // Given
            OrderJpaEntity entity1 = buildOrderEntity();

            OrderJpaEntity entity2 = new OrderJpaEntity();
            entity2.setResourceId("order-def-456");
            entity2.setCustomerId(CUSTOMER_ID);
            entity2.setStatus(OrderStatus.CONFIRMED);
            entity2.setShippingAddress("456 Oak Ave");
            entity2.setCreatedAt(Instant.parse("2026-01-16T12:00:00Z"));
            entity2.setItems(new ArrayList<>());

            when(orderRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(entity1, entity2));

            // When
            List<Order> result = orderJpaDataStore.findByCustomerId(CUSTOMER_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(result.get(1).getResourceId()).isEqualTo("order-def-456");
            verify(orderRepository).findByCustomerId(CUSTOMER_ID);
        }

        @Test
        @DisplayName("should return empty list when customer has no orders")
        void shouldReturnEmptyList_whenCustomerHasNoOrders() {
            // Given
            when(orderRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

            // When
            List<Order> result = orderJpaDataStore.findByCustomerId(CUSTOMER_ID);

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findByCustomerId(CUSTOMER_ID);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return list of mapped domain Orders")
        void shouldReturnMappedDomainList() {
            // Given
            OrderJpaEntity entity1 = buildOrderEntity();

            OrderJpaEntity entity2 = new OrderJpaEntity();
            entity2.setResourceId("order-def-456");
            entity2.setCustomerId("customer-abc-789");
            entity2.setStatus(OrderStatus.CONFIRMED);
            entity2.setShippingAddress("456 Oak Ave, Othertown, USA");
            entity2.setCreatedAt(Instant.parse("2026-01-16T12:00:00Z"));
            entity2.setItems(new ArrayList<>());

            when(orderRepository.findAll()).thenReturn(List.of(entity1, entity2));

            // When
            List<Order> result = orderJpaDataStore.findAll();

            // Then
            assertThat(result).hasSize(2);

            assertThat(result.get(0).getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.get(0).getItems()).hasSize(1);

            assertThat(result.get(1).getResourceId()).isEqualTo("order-def-456");
            assertThat(result.get(1).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(result.get(1).getItems()).isEmpty();

            verify(orderRepository).findAll();
        }

        @Test
        @DisplayName("should return empty list when no orders exist")
        void shouldReturnEmptyList_whenNoOrdersExist() {
            // Given
            when(orderRepository.findAll()).thenReturn(List.of());

            // When
            List<Order> result = orderJpaDataStore.findAll();

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findAll();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should persist entity with all fields and return mapped domain")
        void shouldPersistEntityWithAllFields_andReturnMappedDomain() {
            // Given
            Order domain = buildOrderDomain();
            OrderJpaEntity savedEntity = buildOrderEntity();

            ArgumentCaptor<OrderJpaEntity> entityCaptor = ArgumentCaptor.forClass(OrderJpaEntity.class);
            when(orderRepository.save(entityCaptor.capture())).thenReturn(savedEntity);

            // When
            Order result = orderJpaDataStore.save(domain);

            // Then
            OrderJpaEntity captured = entityCaptor.getValue();
            assertThat(captured.getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(captured.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(captured.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(captured.getShippingAddress()).isEqualTo(SHIPPING_ADDRESS);
            assertThat(captured.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(captured.getItems()).hasSize(1);

            OrderItemJpaEntity capturedItem = captured.getItems().get(0);
            assertThat(capturedItem.getProductId()).isEqualTo("prod-001");
            assertThat(capturedItem.getQuantity()).isEqualTo(2);
            assertThat(capturedItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
            assertThat(capturedItem.getOrder()).isSameAs(captured);

            assertThat(result.getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("should persist entity with empty items list")
        void shouldPersistEntityWithEmptyItems() {
            // Given
            Order domain = Order.builder()
                    .resourceId(RESOURCE_ID)
                    .customerId(CUSTOMER_ID)
                    .status(OrderStatus.PENDING)
                    .shippingAddress(SHIPPING_ADDRESS)
                    .createdAt(CREATED_AT)
                    .items(List.of())
                    .build();

            OrderJpaEntity savedEntity = new OrderJpaEntity();
            savedEntity.setResourceId(RESOURCE_ID);
            savedEntity.setCustomerId(CUSTOMER_ID);
            savedEntity.setStatus(OrderStatus.PENDING);
            savedEntity.setShippingAddress(SHIPPING_ADDRESS);
            savedEntity.setCreatedAt(CREATED_AT);
            savedEntity.setItems(new ArrayList<>());

            ArgumentCaptor<OrderJpaEntity> entityCaptor = ArgumentCaptor.forClass(OrderJpaEntity.class);
            when(orderRepository.save(entityCaptor.capture())).thenReturn(savedEntity);

            // When
            Order result = orderJpaDataStore.save(domain);

            // Then
            assertThat(entityCaptor.getValue().getItems()).isEmpty();
            assertThat(result.getItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update order status and return mapped domain when entity is found")
        void shouldUpdateStatus_andReturnMappedDomain_whenEntityFound() {
            // Given
            OrderJpaEntity existingEntity = buildOrderEntity();
            Order updateDomain = Order.builder()
                    .resourceId(RESOURCE_ID)
                    .customerId(CUSTOMER_ID)
                    .status(OrderStatus.CONFIRMED)
                    .shippingAddress(SHIPPING_ADDRESS)
                    .createdAt(CREATED_AT)
                    .items(List.of())
                    .build();

            OrderJpaEntity savedEntity = buildOrderEntity();
            savedEntity.setStatus(OrderStatus.CONFIRMED);

            when(orderRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.of(existingEntity));
            when(orderRepository.save(existingEntity)).thenReturn(savedEntity);

            // When
            Optional<Order> result = orderJpaDataStore.update(updateDomain);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(existingEntity.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            verify(orderRepository).findByResourceId(RESOURCE_ID);
            verify(orderRepository).save(existingEntity);
        }

        @Test
        @DisplayName("should return empty Optional when order is not found")
        void shouldReturnEmptyOptional_whenEntityNotFound() {
            // Given
            Order updateDomain = buildOrderDomain();
            when(orderRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.empty());

            // When
            Optional<Order> result = orderJpaDataStore.update(updateDomain);

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findByResourceId(RESOURCE_ID);
        }
    }

    @Nested
    @DisplayName("deleteByResourceId")
    class DeleteByResourceId {

        @Test
        @DisplayName("should delete order and return copy of deleted order when order exists")
        void shouldDeleteEntity_andReturnMappedDomain_whenEntityFound() {
            // Given
            OrderJpaEntity entity = buildOrderEntity();
            when(orderRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.of(entity));

            // When
            Optional<Order> result = orderJpaDataStore.deleteByResourceId(RESOURCE_ID);

            // Then
            assertThat(result).isPresent();
            Order deletedOrder = result.get();
            assertThat(deletedOrder.getResourceId()).isEqualTo(RESOURCE_ID);
            assertThat(deletedOrder.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(deletedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

            verify(orderRepository).findByResourceId(RESOURCE_ID);
            verify(orderRepository).delete(entity);
        }

        @Test
        @DisplayName("Should return empty when the order is not found")
        void shouldReturnEmptyOptional_whenEntityNotFound() {
            // Given
            when(orderRepository.findByResourceId(RESOURCE_ID)).thenReturn(Optional.empty());

            // When
            Optional<Order> result = orderJpaDataStore.deleteByResourceId(RESOURCE_ID);

            // Then
            assertThat(result).isEmpty();
            verify(orderRepository).findByResourceId(RESOURCE_ID);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("Should throw exception when the order id is null")
        void shouldThrowException_whenIdIsNull(String resourceId) {
            assertThatThrownBy(() -> orderJpaDataStore.deleteByResourceId(resourceId))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
