/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.port.outbound;

import java.util.Optional;

public interface IdempotencyKeyStore {
    void save(String key, String orderId);

    Optional<String> findOrderId(String key);
}
