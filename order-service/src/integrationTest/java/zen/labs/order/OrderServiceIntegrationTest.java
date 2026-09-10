/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order;

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
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class OrderServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private OutboxRepository outboxRepository;

    @Value("${application.saga.topics.order-events}")
    private String orderEventsTopic;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("context loads")
    void contextLoads() {
        assertThat(placeOrderUseCase).isNotNull();
    }

    @Test
    @DisplayName("placeOrder writes ORDER_PLACED outbox row with correct routing fields")
    void placeOrder_writesOrderPlacedOutboxRow() {
        var cmd = new PlaceOrderCommand(
                UUID.randomUUID().toString(),
                "customer-1",
                List.of(new PlaceOrderCommand.Item("product-1", 2, BigDecimal.valueOf(25))),
                "123 Main St, Springfield, IL 62701");

        var order = placeOrderUseCase.placeOrder(cmd);

        var rows = outboxRepository.findAll().stream()
                .filter(r -> order.getResourceId().equals(r.getAggregateId()))
                .toList();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getAggregateType()).isEqualTo(orderEventsTopic);
        assertThat(row.getType()).isEqualTo("ORDER_PLACED");
        assertThat(row.getPayload()).contains("ORDER_PLACED");
        assertThat(row.getCreatedAt()).isNotNull();
    }
}
