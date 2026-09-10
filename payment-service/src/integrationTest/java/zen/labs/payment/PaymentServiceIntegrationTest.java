/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static zen.labs.infrastructure.profiles.SpringProfiles.INTEGRATION_TEST;
import static zen.labs.infrastructure.profiles.SpringProfiles.TEST;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.payment.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.payment.application.port.inbound.HandlePaymentEventUseCase;
import zen.labs.payment.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class PaymentServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HandlePaymentEventUseCase eventUseCase;

    @Autowired
    private OutboxRepository outboxRepository;

    @Value("${application.saga.topics.payment-events}")
    private String paymentEventsTopic;

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
    @DisplayName("handleInventoryReserved with amount within limit writes PAYMENT_PROCESSED outbox row")
    void handleInventoryReserved_validAmount_writesPaymentProcessedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleInventoryReserved(orderId, BigDecimal.valueOf(500), "123 Main St, Springfield, IL 62701");

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getAggregateId()).isEqualTo(orderId);
        assertThat(row.getAggregateType()).isEqualTo(paymentEventsTopic);
        assertThat(row.getType()).isEqualTo("PAYMENT_PROCESSED");
        assertThat(row.getPayload()).contains(orderId);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handleInventoryReserved with amount over 10000 writes PAYMENT_FAILED outbox row")
    void handleInventoryReserved_excessAmount_writesPaymentFailedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleInventoryReserved(orderId, BigDecimal.valueOf(10_001), "123 Main St");

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("handleShipmentFailed always writes PAYMENT_REFUNDED outbox row")
    void handleShipmentFailed_alwaysWritesPaymentRefundedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleShipmentFailed(orderId);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_REFUNDED");
    }
}
