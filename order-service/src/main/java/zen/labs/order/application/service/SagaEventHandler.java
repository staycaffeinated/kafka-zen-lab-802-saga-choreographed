package zen.labs.order.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaEventHandler implements HandleSagaEventUseCase {

    private final OrderDataStore orderDataStore;

    @Transactional
    public void onInventoryReservationFailed(String orderId) {
        orderDataStore.update(requireOrder(orderId).cancel());
        log.info("Order CANCELLED (inventory failed): orderId={}", orderId);
    }

    @Transactional
    public void onInventoryReleased(String orderId) {
        var order = requireOrder(orderId);
        if (!order.isConfirmed()) {
            orderDataStore.update(order.cancel());
            log.info("Order CANCELLED (inventory released): orderId={}", orderId);
        }
    }

    @Transactional
    public void onShipmentCreated(String orderId) {
        var order = requireOrder(orderId);
        if (order.isConfirming()) {
            orderDataStore.update(order.confirm());
            log.info("Order CONFIRMED: orderId={}", orderId);
        }
    }

    private Order requireOrder(String orderId) {
        return orderDataStore
                .findByResourceId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }
}
