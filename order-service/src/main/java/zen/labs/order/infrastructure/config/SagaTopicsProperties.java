/*
 * Copyright 2026 [CopyrightOwner]
 */

package zen.labs.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String orderCommands,
        String paymentCommands,
        String inventoryCommands,
        String shippingCommands,
        String orchestratorReplies) {}
