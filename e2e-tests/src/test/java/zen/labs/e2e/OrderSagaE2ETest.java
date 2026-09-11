/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderSagaE2ETest {

    private static final String ORDER_SERVICE_URL = System.getProperty("order.service.url", "http://localhost:8085");

    private static final Duration SAGA_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    @BeforeAll
    static void configureRestAssured() {
        RestAssured.baseURI = ORDER_SERVICE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void placeOrder_sagaCompletesSuccessfully() {
        String customerId = "customer-" + UUID.randomUUID();

        String orderId = given().contentType(ContentType.JSON)
                .body(orderPayload(customerId))
                .when()
                .post("/api/v1/orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("resourceId");

        assertThat(orderId).isNotBlank();

        await().atMost(SAGA_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            String status = orderStatus(orderId);
            assertThat(status).isIn("CONFIRMED", "CANCELLED");
        });

        assertThat(orderStatus(orderId))
                .as("Choreographed saga should complete without compensation")
                .isEqualTo("CONFIRMED");
    }

    @Test
    void placeOrder_idempotentSubmission_returnsSameOrder() {
        String idempotencyKey = UUID.randomUUID().toString();
        String customerId = "customer-" + UUID.randomUUID();
        String payload = orderPayload(customerId);

        String firstOrderId = given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(payload)
                .when()
                .post("/api/v1/orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("resourceId");

        String secondOrderId = given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(payload)
                .when()
                .post("/api/v1/orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("resourceId");

        assertThat(secondOrderId)
                .as("Duplicate submission with same Idempotency-Key must return same order")
                .isEqualTo(firstOrderId);
    }

    @Test
    void getOrder_returnsPlacedOrder() {
        String customerId = "customer-" + UUID.randomUUID();

        String orderId = given().contentType(ContentType.JSON)
                .body(orderPayload(customerId))
                .when()
                .post("/api/v1/orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("resourceId");

        Response getResponse = given().when()
                .get("/api/v1/orders/{resourceId}", orderId)
                .then()
                .statusCode(200)
                .extract()
                .response();

        assertThat(getResponse.jsonPath().getString("resourceId")).isEqualTo(orderId);
        assertThat(getResponse.jsonPath().getString("customerId")).isEqualTo(customerId);
        assertThat(getResponse.jsonPath().getString("status")).isNotBlank();
    }

    @Test
    void listOrders_returnsOrdersForCustomer() {
        String customerId = "customer-" + UUID.randomUUID();

        String orderId = given().contentType(ContentType.JSON)
                .body(orderPayload(customerId))
                .when()
                .post("/api/v1/orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("resourceId");

        List<String> orderIds = given().queryParam("customerId", customerId)
                .when()
                .get("/api/v1/orders")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("resourceId", String.class);

        assertThat(orderIds).contains(orderId);
    }

    @Test
    void cancelOrder_compensationCompletesSuccessfully() {
        String customerId = "customer-" + UUID.randomUUID();

        String orderId = given().contentType(ContentType.JSON)
                .body(orderPayload(customerId))
                .when()
                .post("/api/v1/orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("resourceId");

        // Order starts as CONFIRMING — cancel is valid immediately
        given().when()
                .post("/api/v1/orders/{resourceId}/cancel", orderId)
                .then()
                .statusCode(204);

        await().atMost(SAGA_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            String status = orderStatus(orderId);
            assertThat(status).isIn("CANCELLED", "CANCELLING");
        });

        await().atMost(SAGA_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> assertThat(orderStatus(orderId))
                .as("Compensation should complete and order reach CANCELLED")
                .isEqualTo("CANCELLED"));
    }

    private static String orderStatus(String orderId) {
        return given().when()
                .get("/api/v1/orders/{resourceId}", orderId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("status");
    }

    private static String orderPayload(String customerId) {
        return """
                {
                  "customerId": "%s",
                  "shippingAddress": "123 Test Street, Springfield, 12345",
                  "items": [
                    { "productId": "prod-widget-001", "quantity": 2, "unitPrice": 19.99 }
                  ]
                }
                """
                .formatted(customerId);
    }
}
