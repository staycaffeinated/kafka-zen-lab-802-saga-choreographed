/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.outbound.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import zen.labs.inventory.adapter.outbound.persistence.entity.InventoryReservationJpaEntity;
import zen.labs.inventory.adapter.outbound.persistence.repository.InventoryReservationRepository;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.domain.InventoryReservation;

@Component
@RequiredArgsConstructor
public class InventoryReservationJpaDataStore implements InventoryReservationDataStore {

    private final InventoryReservationRepository repository;

    @Override
    public InventoryReservation save(InventoryReservation domain) {
        return toDomain(repository.save(toEntity(domain)));
    }

    @Override
    public Optional<InventoryReservation> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Optional<InventoryReservation> update(InventoryReservation domain) {
        return repository
                .findByOrderId(domain.getOrderId())
                .map(entity -> {
                    entity.setStatus(domain.getStatus());
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    private InventoryReservation toDomain(InventoryReservationJpaEntity e) {
        return InventoryReservation.builder()
                .orderId(e.getOrderId())
                .status(e.getStatus())
                .reservedAt(e.getReservedAt())
                .build();
    }

    private InventoryReservationJpaEntity toEntity(InventoryReservation d) {
        var e = new InventoryReservationJpaEntity();
        e.setOrderId(d.getOrderId());
        e.setStatus(d.getStatus());
        e.setReservedAt(d.getReservedAt());
        return e;
    }
}
