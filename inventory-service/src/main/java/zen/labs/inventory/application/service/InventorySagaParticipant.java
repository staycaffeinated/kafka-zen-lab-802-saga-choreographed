/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.application.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.adapters.common.kafka.SagaReplyType;
import zen.labs.inventory.application.port.inbound.HandleInventoryCommandUseCase;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.application.port.outbound.SagaReplyPublisher;
import zen.labs.inventory.domain.InventoryReservation;
import zen.labs.inventory.domain.ReservationStatus;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InventorySagaParticipant implements HandleInventoryCommandUseCase {

    private final InventoryReservationDataStore reservationDataStore;
    private final SagaReplyPublisher replyPublisher;

    @Override
    public void reserveInventory(String sagaId, String orderId, List<Item> items) {
        var exceeded = items.stream().filter(i -> i.quantity() > 100).findFirst();
        if (exceeded.isPresent()) {
            log.warn(
                    "Inventory reservation failed: sagaId={} productId={} quantity={}",
                    sagaId,
                    exceeded.get().productId(),
                    exceeded.get().quantity());
            replyPublisher.publish(
                    SagaReplyType.INVENTORY_RESERVATION_FAILED.name(),
                    sagaId,
                    orderId,
                    false,
                    "Insufficient stock for product: " + exceeded.get().productId());
            return;
        }

        var reservation = InventoryReservation.builder()
                .sagaId(sagaId)
                .orderId(orderId)
                .status(ReservationStatus.RESERVED)
                .reservedAt(Instant.now())
                .build();
        reservationDataStore.save(reservation);

        log.info("Inventory reserved: sagaId={} orderId={}", sagaId, orderId);
        replyPublisher.publish(SagaReplyType.INVENTORY_RESERVED.name(), sagaId, orderId, true, null);
    }

    @Override
    public void releaseInventory(String sagaId, String orderId) {
        reservationDataStore
                .findBySagaId(sagaId)
                .ifPresentOrElse(
                        r -> {
                            reservationDataStore.update(r.withStatus(ReservationStatus.RELEASED));
                            log.info("Inventory released: sagaId={} orderId={}", sagaId, orderId);
                        },
                        () -> log.warn("No reservation found to release: sagaId={}", sagaId));
        replyPublisher.publish(SagaReplyType.INVENTORY_RELEASED.name(), sagaId, orderId, true, null);
    }
}
