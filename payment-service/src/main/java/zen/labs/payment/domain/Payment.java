/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
public class Payment {
    private final String sagaId;
    private final String orderId;
    private final BigDecimal amount;

    @With
    private final PaymentStatus status;

    private final Instant processedAt;
}
