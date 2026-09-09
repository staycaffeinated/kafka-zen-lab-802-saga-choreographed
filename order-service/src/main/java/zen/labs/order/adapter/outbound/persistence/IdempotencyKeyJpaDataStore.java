/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import zen.labs.order.adapter.outbound.persistence.entity.IdempotencyKeyJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.IdempotencyKeyRepository;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyJpaDataStore implements IdempotencyKeyStore {

    private final IdempotencyKeyRepository repository;

    @Override
    public void save(String key, String orderId) {
        var entity = new IdempotencyKeyJpaEntity();
        entity.setKey(key);
        entity.setOrderId(orderId);
        entity.setCreatedAt(Instant.now());
        try {
            repository.save(entity);
        } catch (DataIntegrityViolationException _) {
            // Concurrent request with the same key already saved — safe to ignore.
            log.warn("Idempotency key already stored (concurrent request): key={}", key);
        }
    }

    @Override
    public Optional<String> findOrderId(String key) {
        return repository.findByKey(key).map(IdempotencyKeyJpaEntity::getOrderId);
    }
}
