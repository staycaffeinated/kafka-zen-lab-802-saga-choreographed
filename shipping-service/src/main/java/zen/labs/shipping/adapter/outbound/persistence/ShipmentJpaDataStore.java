/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.outbound.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import zen.labs.shipping.adapter.outbound.persistence.entity.ShipmentJpaEntity;
import zen.labs.shipping.adapter.outbound.persistence.repository.ShipmentRepository;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.domain.Shipment;

@Component
@RequiredArgsConstructor
public class ShipmentJpaDataStore implements ShipmentDataStore {

    private final ShipmentRepository repository;

    @Override
    public Shipment save(Shipment domain) {
        return toDomain(repository.save(toEntity(domain)));
    }

    @Override
    public Optional<Shipment> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Optional<Shipment> update(Shipment domain) {
        return repository
                .findByOrderId(domain.getOrderId())
                .map(entity -> {
                    entity.setStatus(domain.getStatus());
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    private Shipment toDomain(ShipmentJpaEntity e) {
        return Shipment.builder()
                .orderId(e.getOrderId())
                .shippingAddress(e.getShippingAddress())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private ShipmentJpaEntity toEntity(Shipment d) {
        var e = new ShipmentJpaEntity();
        e.setOrderId(d.getOrderId());
        e.setShippingAddress(d.getShippingAddress());
        e.setStatus(d.getStatus());
        e.setCreatedAt(d.getCreatedAt());
        return e;
    }
}
