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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.order.adapter.inbound.web.order.OrderResponse;
import zen.labs.order.adapter.outbound.persistence.repository.OutboxRepository;
import zen.labs.order.application.port.inbound.HandleSagaEventUseCase;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.domain.Order;
import zen.labs.order.persistence.AbstractPostgresIntegrationTest;

@ActiveProfiles({TEST, INTEGRATION_TEST})
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("OrderController")
class OrderControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String BASE_URL = "/api/v1/orders";

    private final MockMvcTester mvc;
    private final PlaceOrderUseCase placeOrderUseCase;
    private final HandleSagaEventUseCase sagaEventHandler;
    private final OutboxRepository outboxRepository;

    @Autowired
    OrderControllerIntegrationTest(
            MockMvcTester mvc,
            PlaceOrderUseCase placeOrderUseCase,
            HandleSagaEventUseCase sagaEventHandler,
            OutboxRepository outboxRepository) {
        this.mvc = mvc;
        this.placeOrderUseCase = placeOrderUseCase;
        this.sagaEventHandler = sagaEventHandler;
        this.outboxRepository = outboxRepository;
    }

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class PlaceOrder {

        @Test
        @DisplayName("returns 201 with order body and writes outbox row")
        void returns201_andWritesOutboxRow() {
            var body = orderRequestBody("customer-001", "product-A", 2, "12.50", "42 Elm Street");

            postOrder(body)
                    .assertThat()
                    .hasStatus(HttpStatus.CREATED)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .bodyJson()
                    .extractingPath("$")
                    .convertTo(OrderResponse.class)
                    .satisfies(r -> assertThat(r.resourceId()).isNotBlank())
                    .satisfies(r -> assertThat(r.status()).isEqualTo("CONFIRMING"))
                    .satisfies(r -> assertThat(r.items()).hasSize(1));

            assertThat(outboxRepository.findAll()).hasSize(1).first().satisfies(row -> {
                assertThat(row.getType()).isEqualTo("ORDER_PLACED");
                assertThat(row.getCreatedAt()).isNotNull();
            });
        }

        @Test
        @DisplayName("returns same order on duplicate idempotency key — writes outbox row only once")
        void returnsExistingOrder_onDuplicateIdempotencyKey() {
            var idempotencyKey = UUID.randomUUID().toString();
            var body = orderRequestBody("customer-001", "product-A", 1, "10.00", "42 Elm Street");

            postOrderWithKey(body, idempotencyKey).assertThat().hasStatus(HttpStatus.CREATED);
            postOrderWithKey(body, idempotencyKey).assertThat().hasStatus(HttpStatus.CREATED);

            assertThat(outboxRepository.findAll())
                    .as("duplicate submission must not write a second outbox row")
                    .hasSize(1);
        }

        @Test
        @DisplayName("returns 400 when customerId is blank")
        void returns400_whenCustomerIdBlank() {
            postOrder(
                            """
                    {"customerId":"","items":[{"productId":"p-1","quantity":1,"unitPrice":10.00}],"shippingAddress":"42 Elm Street"}
                    """)
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 when items list is empty")
        void returns400_whenItemsEmpty() {
            postOrder(
                            """
                    {"customerId":"customer-001","items":[],"shippingAddress":"42 Elm Street"}
                    """)
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 when shippingAddress is blank")
        void returns400_whenShippingAddressBlank() {
            postOrder(
                            """
                    {"customerId":"customer-001","items":[{"productId":"p-1","quantity":1,"unitPrice":10.00}],"shippingAddress":""}
                    """)
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{resourceId}")
    class GetOrder {

        @Test
        @DisplayName("returns 200 with order body when found")
        void returns200_whenFound() {
            var order = placeOrder("customer-001");

            mvc.get()
                    .uri(BASE_URL + "/{id}", order.getResourceId())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .extractingPath("$")
                    .convertTo(OrderResponse.class)
                    .satisfies(r -> assertThat(r.resourceId()).isEqualTo(order.getResourceId()))
                    .satisfies(r -> assertThat(r.status()).isEqualTo("CONFIRMING"));
        }

        @Test
        @DisplayName("returns 404 when order not found")
        void returns404_whenNotFound() {
            mvc.get()
                    .uri(BASE_URL + "/{id}", "non-existent-order-id")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders?customerId=...")
    class ListOrders {

        @Test
        @DisplayName("returns 200 with all orders for the customer")
        void returns200_withOrders() {
            var customerId = "customer-" + UUID.randomUUID();
            placeOrder(customerId);
            placeOrder(customerId);

            mvc.get()
                    .uri(BASE_URL)
                    .param("customerId", customerId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .extractingPath("$")
                    .asList()
                    .hasSize(2);
        }

        @Test
        @DisplayName("returns 200 with empty array when customer has no orders")
        void returns200_emptyArray_whenNoOrders() {
            mvc.get()
                    .uri(BASE_URL)
                    .param("customerId", "customer-with-no-orders")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .extractingPath("$")
                    .asList()
                    .isEmpty();
        }

        @Test
        @DisplayName("returns 400 when customerId param is missing")
        void returns400_whenCustomerIdMissing() {
            mvc.get()
                    .uri(BASE_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/orders/{resourceId}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("returns 204 when cancellation accepted")
        void returns204_whenCancellationAccepted() {
            var order = placeOrder("customer-001");

            mvc.post()
                    .uri(BASE_URL + "/{id}/cancel", order.getResourceId())
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("returns 422 when order is already CONFIRMED")
        void returns422_whenNotCancellable() {
            var order = placeOrder("customer-001");
            sagaEventHandler.onShipmentCreated(order.getResourceId());

            mvc.post()
                    .uri(BASE_URL + "/{id}/cancel", order.getResourceId())
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("returns 404 when order not found")
        void returns404_whenNotFound() {
            mvc.post()
                    .uri(BASE_URL + "/{id}/cancel", "non-existent-order-id")
                    .exchange()
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    private MvcTestResult postOrder(String body) {
        return mvc.post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult postOrderWithKey(String body, String idempotencyKey) {
        return mvc.post()
                .uri(BASE_URL)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private Order placeOrder(String customerId) {
        return placeOrderUseCase.placeOrder(new PlaceOrderCommand(
                UUID.randomUUID().toString(),
                customerId,
                List.of(new PlaceOrderCommand.Item("product-1", 2, BigDecimal.valueOf(25))),
                "123 Main St, Springfield, IL 62701"));
    }

    private String orderRequestBody(
            String customerId, String productId, int quantity, String unitPrice, String shippingAddress) {
        return """
                {"customerId":"%s","items":[{"productId":"%s","quantity":%d,"unitPrice":%s}],"shippingAddress":"%s"}
                """
                .formatted(customerId, productId, quantity, unitPrice, shippingAddress)
                .strip();
    }
}
