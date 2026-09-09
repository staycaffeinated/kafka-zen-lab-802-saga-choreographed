/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.application.port.outbound;

import java.util.Optional;
import zen.labs.payment.domain.Payment;

public interface PaymentDataStore {
    Payment save(Payment payment);

    Optional<Payment> findBySagaId(String sagaId);

    Optional<Payment> update(Payment payment);
}
