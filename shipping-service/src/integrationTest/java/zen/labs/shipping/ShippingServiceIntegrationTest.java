/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static zen.labs.infrastructure.profiles.SpringProfiles.INTEGRATION_TEST;
import static zen.labs.infrastructure.profiles.SpringProfiles.TEST;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.shipping.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.shipping.application.port.inbound.HandleShipmentEventUseCase;
import zen.labs.shipping.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class ShippingServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HandleShipmentEventUseCase eventUseCase;

    @Autowired
    private OutboxRepository outboxRepository;

    @Value("${application.saga.topics.shipping-events}")
    private String shippingEventsTopic;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("context loads")
    void contextLoads() {
        assertThat(eventUseCase).isNotNull();
    }

    @Test
    @DisplayName("handlePaymentProcessed with valid address writes SHIPMENT_CREATED outbox row")
    void handlePaymentProcessed_validAddress_writesShipmentCreatedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handlePaymentProcessed(orderId, "123 Main St, Springfield, IL 62701");

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getAggregateId()).isEqualTo(orderId);
        assertThat(row.getAggregateType()).isEqualTo(shippingEventsTopic);
        assertThat(row.getType()).isEqualTo("SHIPMENT_CREATED");
        assertThat(row.getPayload()).contains(orderId);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handlePaymentProcessed with null address writes SHIPMENT_FAILED outbox row")
    void handlePaymentProcessed_nullAddress_writesShipmentFailedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handlePaymentProcessed(orderId, null);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("SHIPMENT_FAILED");
    }

    @Test
    @DisplayName("handlePaymentProcessed with address over 200 chars writes SHIPMENT_FAILED outbox row")
    void handlePaymentProcessed_addressTooLong_writesShipmentFailedRow() {
        var orderId = "order-" + UUID.randomUUID();
        var longAddress = "A".repeat(201);

        eventUseCase.handlePaymentProcessed(orderId, longAddress);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("SHIPMENT_FAILED");
    }
}
