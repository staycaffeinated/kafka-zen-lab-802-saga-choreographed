/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.adapters.common.kafka.SagaReplyType;
import zen.labs.shipping.application.port.inbound.HandleShipmentCommandUseCase;
import zen.labs.shipping.application.port.outbound.SagaReplyPublisher;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ShipmentSagaParticipant implements HandleShipmentCommandUseCase {

    private final ShipmentDataStore shipmentDataStore;
    private final SagaReplyPublisher replyPublisher;

    static final int MAX_ADDRESS_LENGTH = 200;

    @Override
    public void createShipment(String sagaId, String orderId, String shippingAddress) {
        if (shippingAddress.length() > MAX_ADDRESS_LENGTH) {
            log.warn("Shipment address exceeds maximum length: sagaId={} length={}", sagaId, shippingAddress.length());
            replyPublisher.publish(
                    SagaReplyType.SHIPMENT_FAILED.name(),
                    sagaId,
                    orderId,
                    false,
                    "Shipping address exceeds maximum length of " + MAX_ADDRESS_LENGTH);
            return;
        }

        var shipment = Shipment.builder()
                .sagaId(sagaId)
                .orderId(orderId)
                .shippingAddress(shippingAddress)
                .status(ShipmentStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        shipmentDataStore.save(shipment);

        log.info("Shipment created: sagaId={} orderId={} address={}", sagaId, orderId, shippingAddress);
        replyPublisher.publish(SagaReplyType.SHIPMENT_CREATED.name(), sagaId, orderId, true, null);
    }
}
