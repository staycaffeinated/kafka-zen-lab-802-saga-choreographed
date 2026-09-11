package zen.labs.order.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderDataStore orderDataStore;
    private final OrderEventPublisher eventPublisher;
    private final ResourceIdSupplier resourceIdSupplier;
    private final IdempotencyKeyStore idempotencyKeyStore;

    @Override
    @Transactional
    public Order placeOrder(PlaceOrderCommand cmd) {
        if (cmd.idempotencyKey() != null) {
            var existingId = idempotencyKeyStore.findOrderId(cmd.idempotencyKey());
            if (existingId.isPresent()) {
                log.info("===> Order already placed: existingId: {}", cmd.idempotencyKey());
                return orderDataStore
                        .findByResourceId(existingId.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + existingId.get()));
            }
        }

        var order = Order.builder()
                .resourceId(resourceIdSupplier.nextResourceId())
                .customerId(cmd.customerId())
                .items(cmd.items().stream()
                        .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
                        .toList())
                .shippingAddress(cmd.shippingAddress())
                .status(OrderStatus.CONFIRMING)
                .createdAt(Instant.now())
                .build();
        var saved = orderDataStore.save(order);

        if (cmd.idempotencyKey() != null) {
            idempotencyKeyStore.save(cmd.idempotencyKey(), saved.getResourceId());
            log.info("===> Order saved to idempotency store: key: {}", cmd.idempotencyKey());
        }

        eventPublisher.publishOrderPlaced(
                saved.getResourceId(), saved.getItems(), saved.totalAmount(), saved.getShippingAddress());
        log.info("===> Order placed: orderId={}", saved.getResourceId());
        return saved;
    }
}
