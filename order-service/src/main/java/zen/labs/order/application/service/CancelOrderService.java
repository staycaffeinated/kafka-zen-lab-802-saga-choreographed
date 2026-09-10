package zen.labs.order.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderDataStore orderDataStore;
    private final OrderEventPublisher eventPublisher;

    @Override
    @Transactional
    public void cancelOrder(String orderId) {
        var order = orderDataStore
                .findByResourceId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.isConfirming()) {
            throw new UnprocessableRequestException("Order cannot be cancelled in status: " + order.getStatus());
        }
        orderDataStore.update(order.startCancellation());
        eventPublisher.publishCancellationRequested(orderId);
        log.info("Order cancellation requested: orderId={}", orderId);
    }
}
