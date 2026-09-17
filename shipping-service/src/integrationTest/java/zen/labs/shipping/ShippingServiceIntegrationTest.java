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
import zen.labs.shipping.adapter.outbound.persistence.repository.ShipmentRepository;
import zen.labs.shipping.application.port.inbound.HandleShipmentEventUseCase;
import zen.labs.shipping.domain.ShipmentStatus;
import zen.labs.shipping.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class ShippingServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HandleShipmentEventUseCase eventUseCase;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Value("${application.saga.topics.shipping-events}")
    private String shippingEventsTopic;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        shipmentRepository.deleteAll();
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
        assertThat(row.getAggregateType()).isEqualTo(shippingEventsTopic);
        assertThat(row.getType()).isEqualTo("SHIPMENT_CREATED");
        assertThat(row.getPayload()).contains(orderId);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handlePaymentProcessed with valid address saves shipment as CREATED")
    void handlePaymentProcessed_validAddress_savesShipment() {
        var orderId = "order-" + UUID.randomUUID();
        var address = "123 Main St, Springfield, IL 62701";

        eventUseCase.handlePaymentProcessed(orderId, address);

        var shipment = shipmentRepository.findByOrderId(orderId);
        assertThat(shipment).isPresent();
        assertThat(shipment.get().getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(shipment.get().getShippingAddress()).isEqualTo(address);
        assertThat(shipment.get().getCreatedAt()).isNotNull();
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
    @DisplayName("handlePaymentProcessed with null address does not save shipment")
    void handlePaymentProcessed_nullAddress_doesNotSaveShipment() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handlePaymentProcessed(orderId, null);

        assertThat(shipmentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    @DisplayName("handlePaymentProcessed with address over 200 chars writes SHIPMENT_FAILED outbox row")
    void handlePaymentProcessed_addressTooLong_writesShipmentFailedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handlePaymentProcessed(orderId, "A".repeat(201));

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("SHIPMENT_FAILED");
    }

    @Test
    @DisplayName("handlePaymentProcessed called twice with same orderId is idempotent — one shipment, one outbox row")
    void handlePaymentProcessed_calledTwice_isIdempotent() {
        var orderId = "order-" + UUID.randomUUID();
        var address = "123 Main St, Springfield, IL 62701";

        eventUseCase.handlePaymentProcessed(orderId, address);
        eventUseCase.handlePaymentProcessed(orderId, address);

        assertThat(shipmentRepository.findAll().stream()
                        .filter(s -> s.getOrderId().equals(orderId))
                        .toList())
                .hasSize(1);
        assertThat(outboxRepository.findAll().stream()
                        .filter(r -> r.getAggregateId().equals(orderId))
                        .toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("handleOrderCancellationRequested when shipment exists transitions it to CANCELLED")
    void handleOrderCancellationRequested_withExistingShipment_cancelsShipment() {
        var orderId = "order-" + UUID.randomUUID();
        eventUseCase.handlePaymentProcessed(orderId, "123 Main St");
        outboxRepository.deleteAll();

        eventUseCase.handleOrderCancellationRequested(orderId);

        var shipment = shipmentRepository.findByOrderId(orderId);
        assertThat(shipment).isPresent();
        assertThat(shipment.get().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("handleOrderCancellationRequested when no shipment does nothing")
    void handleOrderCancellationRequested_withNoShipment_doesNothing() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleOrderCancellationRequested(orderId);

        assertThat(shipmentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
    }
}
