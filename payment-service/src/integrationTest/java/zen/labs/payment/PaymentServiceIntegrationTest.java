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
import zen.labs.payment.adapter.outbound.persistence.repository.PaymentRepository;
import zen.labs.payment.application.port.inbound.HandlePaymentEventUseCase;
import zen.labs.payment.domain.PaymentStatus;
import zen.labs.payment.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class PaymentServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HandlePaymentEventUseCase eventUseCase;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Value("${application.saga.topics.payment-events}")
    private String paymentEventsTopic;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("context loads")
    void contextLoads() {
        assertThat(eventUseCase).isNotNull();
    }

    @Test
    @DisplayName("handleInventoryReserved with valid amount writes PAYMENT_PROCESSED outbox row")
    void handleInventoryReserved_validAmount_writesPaymentProcessedRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleInventoryReserved(orderId, BigDecimal.valueOf(500), "123 Main St, Springfield, IL 62701");

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getAggregateType()).isEqualTo(paymentEventsTopic);
        assertThat(row.getType()).isEqualTo("PAYMENT_PROCESSED");
        assertThat(row.getPayload()).contains(orderId);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handleInventoryReserved with valid amount saves payment as PROCESSED")
    void handleInventoryReserved_validAmount_savesPayment() {
        var orderId = "order-" + UUID.randomUUID();
        var amount = BigDecimal.valueOf(500);

        eventUseCase.handleInventoryReserved(orderId, amount, "123 Main St, Springfield, IL 62701");

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
        assertThat(payment.get().getAmount()).isEqualByComparingTo(amount);
        assertThat(payment.get().getProcessedAt()).isNotNull();
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
    @DisplayName("handleInventoryReserved with amount over 10000 does not save a payment record")
    void handleInventoryReserved_excessAmount_doesNotSavePayment() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleInventoryReserved(orderId, BigDecimal.valueOf(10_001), "123 Main St");

        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    @DisplayName("handleInventoryReserved called twice with same orderId is idempotent — one payment, one outbox row")
    void handleInventoryReserved_calledTwice_isIdempotent() {
        var orderId = "order-" + UUID.randomUUID();
        var amount = BigDecimal.valueOf(500);
        var address = "123 Main St, Springfield, IL 62701";

        eventUseCase.handleInventoryReserved(orderId, amount, address);
        eventUseCase.handleInventoryReserved(orderId, amount, address);

        assertThat(paymentRepository.findAll().stream()
                        .filter(p -> p.getOrderId().equals(orderId))
                        .toList())
                .hasSize(1);
        assertThat(outboxRepository.findAll().stream()
                        .filter(r -> r.getAggregateId().equals(orderId))
                        .toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("handleShipmentFailed when payment exists refunds it and writes PAYMENT_REFUNDED outbox row")
    void handleShipmentFailed_withExistingPayment_refundsAndWritesOutboxRow() {
        var orderId = "order-" + UUID.randomUUID();
        eventUseCase.handleInventoryReserved(orderId, BigDecimal.valueOf(500), "123 Main St");
        outboxRepository.deleteAll();

        eventUseCase.handleShipmentFailed(orderId);

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_REFUNDED");
        assertThat(rows.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("handleShipmentFailed when no payment still writes PAYMENT_REFUNDED outbox row")
    void handleShipmentFailed_withNoPayment_stillWritesOutboxRow() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleShipmentFailed(orderId);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    @DisplayName(
            "handleOrderCancellationRequested when payment exists refunds it and writes PAYMENT_REFUNDED outbox row")
    void handleOrderCancellationRequested_withExistingPayment_refundsAndWritesOutboxRow() {
        var orderId = "order-" + UUID.randomUUID();
        eventUseCase.handleInventoryReserved(orderId, BigDecimal.valueOf(500), "123 Main St");
        outboxRepository.deleteAll();

        eventUseCase.handleOrderCancellationRequested(orderId);

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo("PAYMENT_REFUNDED");
        assertThat(rows.get(0).getAggregateType()).isEqualTo(paymentEventsTopic);
    }

    @Test
    @DisplayName("handleOrderCancellationRequested when no payment does nothing")
    void handleOrderCancellationRequested_withNoPayment_doesNothing() {
        var orderId = "order-" + UUID.randomUUID();

        eventUseCase.handleOrderCancellationRequested(orderId);

        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
    }
}
