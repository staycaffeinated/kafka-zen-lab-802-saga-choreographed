/*
 * Copyright 2026 [CopyrightOwner]
 */

package zen.labs.inventory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(String inventoryEvents, String orderEvents, String paymentEvents) {}
