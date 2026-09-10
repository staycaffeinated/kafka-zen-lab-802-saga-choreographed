package zen.labs.order.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaEventHandler implements HandleSagaEventUseCase {

    private final OrderDataStore orderDataStore;

    @Transactional
    public void onInventoryReservationFailed(String orderId) {
        updateStatus(orderId, OrderStatus.CANCELLED);
        log.info("Order CANCELLED (inventory failed): orderId={}", orderId);
    }

    @Transactional
    public void onInventoryReleased(String orderId) {
        var order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            orderDataStore.update(order.withStatus(OrderStatus.CANCELLED));
            log.info("Order CANCELLED (inventory released): orderId={}", orderId);
        }
    }

    @Transactional
    public void onShipmentCreated(String orderId) {
        var order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMING) {
            orderDataStore.update(order.withStatus(OrderStatus.CONFIRMED));
            log.info("Order CONFIRMED: orderId={}", orderId);
        }
    }

    private void updateStatus(String orderId, OrderStatus status) {
        orderDataStore.update(requireOrder(orderId).withStatus(status));
    }

    private Order requireOrder(String orderId) {
        return orderDataStore
                .findByResourceId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }
}
