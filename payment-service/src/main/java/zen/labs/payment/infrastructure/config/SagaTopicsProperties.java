/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String paymentEvents, String inventoryEvents, String shippingEvents, String orderEvents) {}
