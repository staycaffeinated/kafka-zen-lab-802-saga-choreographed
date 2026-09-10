package zen.labs.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String orderEvents, String inventoryEvents, String paymentEvents, String shippingEvents) {}
