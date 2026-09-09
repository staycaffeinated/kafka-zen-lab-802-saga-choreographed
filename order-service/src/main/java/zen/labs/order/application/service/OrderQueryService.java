/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.inbound.FindOrderUseCase;
import zen.labs.order.application.port.inbound.ListOrdersUseCase;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService implements FindOrderUseCase, ListOrdersUseCase {

    private final OrderDataStore orderDataStore;

    @Override
    public Order findOrder(String resourceId) {
        return orderDataStore
                .findByResourceId(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + resourceId));
    }

    @Override
    public List<Order> listOrdersByCustomer(String customerId) {
        return orderDataStore.findByCustomerId(customerId);
    }
}
