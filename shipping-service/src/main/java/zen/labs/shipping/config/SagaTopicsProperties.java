/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(String shippingEvents, String paymentEvents, String orderEvents) {}
