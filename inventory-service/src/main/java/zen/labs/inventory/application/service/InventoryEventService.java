package zen.labs.inventory.application.service;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.inventory.application.port.inbound.HandleInventoryEventUseCase;
import zen.labs.inventory.application.port.inbound.OrderedItem;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.domain.InventoryReservation;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventService implements HandleInventoryEventUseCase {

    private final InventoryReservationDataStore reservationDataStore;
    private final InventoryEventPublisher eventPublisher;

    @Override
    @Transactional
    public void handleOrderPlaced(
            String orderId, List<OrderedItem> items, BigDecimal amount, String shippingAddress) {
        var exceeded = items.stream().filter(i -> i.quantity() > 100).findFirst();
        if (exceeded.isPresent()) {
            log.warn(
                    "Inventory reservation failed: orderId={} productId={}",
                    orderId,
                    exceeded.get().productId());
            eventPublisher.publishInventoryReservationFailed(
                    orderId, "Insufficient stock for product: " + exceeded.get().productId());
            return;
        }
        var reservation = InventoryReservation.forOrder(orderId);
        reservationDataStore.save(reservation);
        log.info("Inventory reserved: orderId={}", orderId);
        eventPublisher.publishInventoryReserved(orderId, amount, shippingAddress);
    }

    @Override
    @Transactional
    public void handleRelease(String orderId) {
        reservationDataStore
                .findByOrderId(orderId)
                .ifPresentOrElse(
                        r -> {
                            reservationDataStore.update(r.release());
                            log.info("Inventory released: orderId={}", orderId);
                        },
                        () -> log.warn("No reservation to release: orderId={}", orderId));
        eventPublisher.publishInventoryReleased(orderId);
    }
}
