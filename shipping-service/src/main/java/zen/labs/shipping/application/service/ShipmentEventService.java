/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.shipping.application.port.inbound.HandleShipmentEventUseCase;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventService implements HandleShipmentEventUseCase {

    static final int MAX_ADDRESS_LENGTH = 200;

    private final ShipmentDataStore shipmentDataStore;
    private final ShippingEventPublisher eventPublisher;

    @Override
    @Transactional
    public void handlePaymentProcessed(String orderId, String shippingAddress) {
        if (shipmentDataStore.findByOrderId(orderId).isPresent()) {
            log.warn("Duplicate PAYMENT_PROCESSED event — shipment already exists: orderId={}", orderId);
            return;
        }
        if (shippingAddress == null || shippingAddress.isBlank()) {
            log.warn("Shipment failed — address missing: orderId={}", orderId);
            eventPublisher.publishShipmentFailed(orderId, "Shipping address is required");
            return;
        }
        if (shippingAddress.length() > MAX_ADDRESS_LENGTH) {
            log.warn("Shipment failed — address too long: orderId={}", orderId);
            eventPublisher.publishShipmentFailed(
                    orderId, "Shipping address exceeds maximum length of " + MAX_ADDRESS_LENGTH);
            return;
        }
        var shipment = Shipment.builder()
                .orderId(orderId)
                .shippingAddress(shippingAddress)
                .status(ShipmentStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        shipmentDataStore.save(shipment);
        log.info("Shipment created: orderId={}", orderId);
        eventPublisher.publishShipmentCreated(orderId);
    }

    @Override
    @Transactional
    public void handleOrderCancellationRequested(String orderId) {
        shipmentDataStore
                .findByOrderId(orderId)
                .ifPresentOrElse(
                        s -> {
                            shipmentDataStore.update(s.withStatus(ShipmentStatus.CANCELLED));
                            log.info("Shipment cancelled: orderId={}", orderId);
                        },
                        () -> log.debug("No shipment to cancel: orderId={}", orderId));
    }
}
