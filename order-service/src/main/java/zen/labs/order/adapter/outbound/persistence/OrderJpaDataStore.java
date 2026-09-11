/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import zen.labs.order.adapter.outbound.persistence.entity.OrderItemJpaEntity;
import zen.labs.order.adapter.outbound.persistence.entity.OrderJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.OrderRepository;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;

@Component
@RequiredArgsConstructor
public class OrderJpaDataStore implements OrderDataStore {

    private final OrderRepository orderRepository;

    @Override
    public Optional<Order> findByResourceId(@NonNull String resourceId) {
        return orderRepository.findByResourceId(resourceId).map(this::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Order save(Order domain) {
        return toDomain(orderRepository.save(toEntity(domain)));
    }

    @Override
    public Optional<Order> update(Order domain) {
        return orderRepository
                .findByResourceId(domain.getResourceId())
                .map(entity -> {
                    entity.setStatus(domain.getStatus());
                    return orderRepository.save(entity);
                })
                .map(this::toDomain);
    }

    private Order toDomain(OrderJpaEntity entity) {
        return Order.builder()
                .resourceId(entity.getResourceId())
                .customerId(entity.getCustomerId())
                .status(entity.getStatus())
                .shippingAddress(entity.getShippingAddress())
                .createdAt(entity.getCreatedAt())
                .items(entity.getItems().stream()
                        .map(i -> new OrderItem(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                        .toList())
                .build();
    }

    private OrderJpaEntity toEntity(Order domain) {
        var entity = new OrderJpaEntity();
        entity.setResourceId(domain.getResourceId());
        entity.setCustomerId(domain.getCustomerId());
        entity.setStatus(domain.getStatus());
        entity.setShippingAddress(domain.getShippingAddress());
        entity.setCreatedAt(domain.getCreatedAt());
        var items = domain.getItems().stream()
                .map(i -> {
                    var item = new OrderItemJpaEntity();
                    item.setOrder(entity);
                    item.setProductId(i.productId());
                    item.setQuantity(i.quantity());
                    item.setUnitPrice(i.unitPrice());
                    return item;
                })
                .toList();
        entity.setItems(new ArrayList<>(items));
        return entity;
    }
}
