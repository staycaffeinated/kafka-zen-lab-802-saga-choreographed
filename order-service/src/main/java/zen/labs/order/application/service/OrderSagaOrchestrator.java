/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.inbound.HandleSagaReplyUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.SagaCommandPublisher;
import zen.labs.order.application.port.outbound.SagaStateDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;
import zen.labs.order.domain.SagaState;
import zen.labs.order.domain.SagaStatus;
import zen.labs.order.domain.SagaStep;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator implements PlaceOrderUseCase, HandleSagaReplyUseCase, CancelOrderUseCase {

    private final OrderDataStore orderDataStore;
    private final SagaStateDataStore sagaStateDataStore;
    private final SagaCommandPublisher commandPublisher;
    private final ResourceIdSupplier resourceIdSupplier;
    private final IdempotencyKeyStore idempotencyKeyStore;

    @Override
    @Transactional
    public Order placeOrder(PlaceOrderCommand cmd) {
        if (cmd.idempotencyKey() != null) {
            var existingOrderId = idempotencyKeyStore.findOrderId(cmd.idempotencyKey());
            if (existingOrderId.isPresent()) {
                return orderDataStore
                        .findByResourceId(existingOrderId.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + existingOrderId.get()));
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
                .build();
        var savedOrder = orderDataStore.save(order);

        var sagaId = resourceIdSupplier.nextResourceId();
        var saga = SagaState.builder()
                .sagaId(sagaId)
                .orderId(savedOrder.getResourceId())
                .status(SagaStatus.STARTED)
                .currentStep(SagaStep.RESERVE_INVENTORY)
                .updatedAt(Instant.now())
                .build();
        sagaStateDataStore.save(saga);

        if (cmd.idempotencyKey() != null) {
            idempotencyKeyStore.save(cmd.idempotencyKey(), savedOrder.getResourceId());
        }

        commandPublisher.reserveInventory(sagaId, savedOrder.getResourceId(), savedOrder.getItems());

        return savedOrder;
    }

    @Override
    @Transactional
    public void onInventoryReply(String sagaId, String orderId, boolean success, String failureReason) {
        if (success) {
            updateSaga(sagaId, SagaStatus.INVENTORY_RESERVED, SagaStep.PROCESS_PAYMENT);
            var order = requireOrder(orderId);
            commandPublisher.processPayment(sagaId, orderId, order.totalAmount());
        } else {
            log.warn("Inventory reservation failed: sagaId={} reason={}", sagaId, failureReason);
            failSaga(sagaId, orderId);
        }
    }

    @Override
    @Transactional
    public void onPaymentReply(String sagaId, String orderId, boolean success, String failureReason) {
        if (success) {
            updateSaga(sagaId, SagaStatus.PAYMENT_PROCESSED, SagaStep.CREATE_SHIPMENT);
            var order = requireOrder(orderId);
            commandPublisher.createShipment(sagaId, orderId, order.getShippingAddress());
        } else {
            log.warn("Payment failed: sagaId={} reason={}", sagaId, failureReason);
            compensateInventory(sagaId, orderId);
        }
    }

    @Override
    @Transactional
    public void onShipmentReply(String sagaId, String orderId, boolean success, String failureReason) {
        if (success) {
            updateSaga(sagaId, SagaStatus.COMPLETED, SagaStep.DONE);
            updateOrderStatus(orderId, OrderStatus.CONFIRMED);
        } else {
            log.warn("Shipment failed: sagaId={} reason={}", sagaId, failureReason);
            compensatePayment(sagaId, orderId);
        }
    }

    @Override
    @Transactional
    public void cancelOrder(String orderId) {
        var order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMING) {
            throw new UnprocessableRequestException("Order cannot be cancelled in status: " + order.getStatus());
        }
        var saga = sagaStateDataStore
                .findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found for order: " + orderId));

        switch (saga.getStatus()) {
            case INVENTORY_RESERVED -> {
                updateSaga(saga.getSagaId(), SagaStatus.COMPENSATING, SagaStep.COMPENSATE_INVENTORY);
                updateOrderStatus(orderId, OrderStatus.CANCELLING);
                commandPublisher.releaseInventory(saga.getSagaId(), orderId);
            }
            case PAYMENT_PROCESSED -> {
                updateSaga(saga.getSagaId(), SagaStatus.COMPENSATING, SagaStep.COMPENSATE_PAYMENT);
                updateOrderStatus(orderId, OrderStatus.CANCELLING);
                commandPublisher.refundPayment(saga.getSagaId(), orderId);
            }
            default ->
                throw new UnprocessableRequestException(
                        "Order cannot be cancelled in saga status: " + saga.getStatus());
        }
    }

    @Override
    @Transactional
    public void onInventoryReleasedReply(String sagaId, String orderId) {
        failSaga(sagaId, orderId);
    }

    @Override
    @Transactional
    public void onPaymentRefundedReply(String sagaId, String orderId) {
        updateSaga(sagaId, SagaStatus.COMPENSATING, SagaStep.COMPENSATE_INVENTORY);
        commandPublisher.releaseInventory(sagaId, orderId);
    }

    private void compensateInventory(String sagaId, String orderId) {
        updateSaga(sagaId, SagaStatus.COMPENSATING, SagaStep.COMPENSATE_INVENTORY);
        updateOrderStatus(orderId, OrderStatus.CANCELLING);
        commandPublisher.releaseInventory(sagaId, orderId);
    }

    private void compensatePayment(String sagaId, String orderId) {
        updateSaga(sagaId, SagaStatus.COMPENSATING, SagaStep.COMPENSATE_PAYMENT);
        updateOrderStatus(orderId, OrderStatus.CANCELLING);
        commandPublisher.refundPayment(sagaId, orderId);
    }

    private void failSaga(String sagaId, String orderId) {
        updateSaga(sagaId, SagaStatus.FAILED, SagaStep.DONE);
        updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }

    private void updateSaga(String sagaId, SagaStatus status, SagaStep step) {
        var saga = requireSaga(sagaId).transitionTo(status, step, Instant.now());
        sagaStateDataStore.update(saga);
    }

    private SagaState requireSaga(String sagaId) {
        return sagaStateDataStore
                .findBySagaId(sagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found: " + sagaId));
    }

    private Order requireOrder(String orderId) {
        return orderDataStore
                .findByResourceId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void updateOrderStatus(String orderId, OrderStatus status) {
        var order = requireOrder(orderId).withStatus(status);
        orderDataStore.update(order);
    }
}
