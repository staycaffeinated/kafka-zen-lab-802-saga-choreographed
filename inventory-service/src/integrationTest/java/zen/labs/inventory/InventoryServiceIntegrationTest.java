/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static zen.labs.infrastructure.profiles.SpringProfiles.INTEGRATION_TEST;
import static zen.labs.infrastructure.profiles.SpringProfiles.TEST;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.inventory.adapter.outbound.persistence.repository.InventoryReservationRepository;
import zen.labs.inventory.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.inventory.application.port.inbound.HandleInventoryEventUseCase;
import zen.labs.inventory.application.port.inbound.OrderedItem;
import zen.labs.inventory.common.persistence.AbstractPostgresIntegrationTest;
import zen.labs.inventory.domain.ReservationStatus;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class InventoryServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HandleInventoryEventUseCase eventUseCase;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Value("${application.saga.topics.inventory-events}")
    private String inventoryEventsTopic;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    @DisplayName("context loads")
    void contextLoads() {
        assertThat(eventUseCase).isNotNull();
    }

    @Test
    @DisplayName("handleOrderPlaced with valid qty writes INVENTORY_RESERVED outbox row")
    void handleOrderPlaced_validQty_writesInventoryReservedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleOrderPlaced(
                orderId,
                List.of(new OrderedItem("product-1", 5)),
                BigDecimal.valueOf(500),
                "123 Main St, Springfield, IL 62701");

        var rows = outboxRepository.findAll();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getAggregateId()).isEqualTo(orderId);
        assertThat(row.getAggregateType()).isEqualTo(inventoryEventsTopic);
        assertThat(row.getType()).isEqualTo("INVENTORY_RESERVED");
        assertThat(row.getPayload()).contains(orderId);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handleOrderPlaced with valid qty saves reservation as RESERVED")
    void handleOrderPlaced_validQty_savesReservation() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleOrderPlaced(
                orderId,
                List.of(new OrderedItem("product-1", 5)),
                BigDecimal.valueOf(500),
                "123 Main St, Springfield, IL 62701");

        var reservation = reservationRepository.findByOrderId(orderId);
        assertThat(reservation).isPresent();
        assertThat(reservation.get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.get().getReservedAt()).isNotNull();
    }

    @Test
    @DisplayName("handleOrderPlaced with qty > 100 writes INVENTORY_RESERVATION_FAILED outbox row")
    void handleOrderPlaced_excessQty_writesReservationFailedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleOrderPlaced(
                orderId, List.of(new OrderedItem("product-1", 101)), BigDecimal.valueOf(500), "123 Main St");

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RESERVATION_FAILED");
    }

    @Test
    @DisplayName("handleOrderPlaced with qty > 100 does not create a reservation record")
    void handleOrderPlaced_excessQty_doesNotSaveReservation() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleOrderPlaced(
                orderId, List.of(new OrderedItem("product-1", 101)), BigDecimal.valueOf(500), "123 Main St");

        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    @DisplayName("handleRelease when reservation exists transitions it to RELEASED and writes outbox row")
    void handleRelease_withExistingReservation_releasesAndWritesOutboxRow() {
        var orderId = "order-" + UUID.randomUUID();
        eventUseCase.handleOrderPlaced(
                orderId, List.of(new OrderedItem("product-1", 5)), BigDecimal.valueOf(500), "123 Main St");
        outboxRepository.deleteAll();

        eventUseCase.handleRelease(orderId);

        var reservation = reservationRepository.findByOrderId(orderId);
        assertThat(reservation).isPresent();
        assertThat(reservation.get().getStatus()).isEqualTo(ReservationStatus.RELEASED);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RELEASED");
        assertThat(rows.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handleRelease when no reservation still writes INVENTORY_RELEASED outbox row")
    void handleRelease_withNoReservation_stillWritesOutboxRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleRelease(orderId);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("INVENTORY_RELEASED");
    }
}
