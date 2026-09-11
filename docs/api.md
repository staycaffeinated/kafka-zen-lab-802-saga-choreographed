# API Reference

The `order-service` exposes the only public REST API in this project. Participant services (`inventory-service`,
`payment-service`, `shipping-service`) have no REST API beyond health and actuator endpoints; all interaction with
them is via Kafka.

All HTTP errors follow **RFC 7807 Problem Details** (`application/problem+json`).

---

## order-service REST API

### Base URL

```
http://localhost:8080/
```

### Interactive Documentation

When the service is running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

---

### Endpoints

#### POST /api/v1/orders — Place an order

Places a new order and starts the choreographed saga. The order is immediately persisted with status `CONFIRMING`
and an `ORDER_PLACED` event is written to the outbox in the same transaction. Debezium forwards the event to Kafka,
triggering the inventory reservation step.

Supports idempotent submission via the optional `Idempotency-Key` request header. If a key is provided and a
previous submission with that key exists, the original order is returned without re-executing the saga.

```
POST /api/v1/orders HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Idempotency-Key: client-generated-uuid   (optional)

{
  "customerId": "cust-abc123",
  "items": [
    { "productId": "prod-xyz789", "quantity": 2, "unitPrice": 49.99 }
  ],
  "shippingAddress": "123 Main St, Springfield, IL 62701"
}
```

**Request fields:**

| Field | Type | Required | Description |
|---|---|---|---|
| `customerId` | string | yes | Customer resource identifier |
| `items` | array | yes | One or more order items; must not be empty |
| `items[].productId` | string | yes | Product resource identifier |
| `items[].quantity` | integer | yes | Quantity to order |
| `items[].unitPrice` | number | yes | Unit price for this item |
| `shippingAddress` | string | yes | Full shipping address |

**Responses:**

| Status | Description |
|---|---|
| 201 Created | Order created; saga started |
| 400 Bad Request | Invalid request body or constraint violation |

**Example response body (201):**

```json
{
  "resourceId": "res-a1b2c3d4e5",
  "customerId": "cust-abc123",
  "status": "CONFIRMING",
  "shippingAddress": "123 Main St, Springfield, IL 62701",
  "totalAmount": 99.98,
  "items": [
    { "productId": "prod-xyz789", "quantity": 2, "unitPrice": 49.99 }
  ],
  "createdAt": "2026-09-11T10:00:00Z"
}
```

---

#### GET /api/v1/orders/{resourceId} — Get an order

Returns a single order by its resource identifier.

```
GET /api/v1/orders/{resourceId} HTTP/1.1
Host: localhost:8080
```

**Responses:**

| Status | Description |
|---|---|
| 200 OK | Order found |
| 404 Not Found | Order not found |

**Example response body (200):**

```json
{
  "resourceId": "res-a1b2c3d4e5",
  "customerId": "cust-abc123",
  "status": "CONFIRMED",
  "shippingAddress": "123 Main St, Springfield, IL 62701",
  "totalAmount": 99.98,
  "items": [
    { "productId": "prod-xyz789", "quantity": 2, "unitPrice": 49.99 }
  ],
  "createdAt": "2026-09-11T10:00:00Z"
}
```

---

#### POST /api/v1/orders/{resourceId}/cancel — Cancel an order

Initiates saga compensation for an in-progress order. Writes an `ORDER_CANCELLATION_REQUESTED` event to the outbox.
Participant services react by releasing inventory and refunding payment as applicable.

```
POST /api/v1/orders/{resourceId}/cancel HTTP/1.1
Host: localhost:8080
```

**Responses:**

| Status | Description |
|---|---|
| 204 No Content | Cancellation event published; compensation in progress |
| 404 Not Found | Order not found |
| 422 Unprocessable Content | Order not in `CONFIRMING` status |

---

#### GET /api/v1/orders — List orders by customer

Returns all orders placed by the specified customer.

```
GET /api/v1/orders?customerId=cust-abc123 HTTP/1.1
Host: localhost:8080
```

**Query parameters:**

| Parameter | Required | Description |
|---|---|---|
| `customerId` | yes | Customer resource identifier |

**Responses:**

| Status | Description |
|---|---|
| 200 OK | Orders retrieved (may be empty array) |
| 400 Bad Request | Missing or invalid `customerId` parameter |

---

#### GET / — Root health check

```
GET / HTTP/1.1
Host: localhost:8080

HTTP/1.1 200 OK
```

---

#### GET /actuator/health — Actuator health

```json
{ "status": "UP" }
```

---

### Order Status Transitions

| From | To | Trigger |
|---|---|---|
| — | `CONFIRMING` | `POST /api/v1/orders` — `ORDER_PLACED` event published |
| `CONFIRMING` | `CONFIRMED` | `SHIPMENT_CREATED` event received from shipping-service |
| `CONFIRMING` | `CANCELLED` | `INVENTORY_RESERVATION_FAILED` received, or `INVENTORY_RELEASED` received after compensation |

---

### Error Response Format

All HTTP errors use RFC 7807 Problem Details:

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Order not found: res-a1b2c3d4e5"
}
```

Constraint violation errors include an `errors` array:

```json
{
  "type": "about:blank",
  "title": "The request cannot be processed",
  "status": 400,
  "errors": [
    {
      "reason": "must not be blank",
      "invalid value": "",
      "method": "customerId"
    }
  ]
}
```

| HTTP Status | Triggered by |
|---|---|
| 400 Bad Request | `BadRequestException`, `ConstraintViolationException`, missing required parameter |
| 404 Not Found | `ResourceNotFoundException` |
| 422 Unprocessable Content | `UnprocessableRequestException`, `DataIntegrityViolationException`, `TransactionSystemException` |
| 500 Internal Server Error | Uncaught `RuntimeException` |

---

## Kafka Message Contracts

All Kafka messages use the shared `SagaEvent` record serialised to JSON. The `orderId` is used as the Kafka message
key (`aggregate_id` column in the outbox), ensuring per-order ordering within a partition.

### SagaEvent — shared message envelope

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "ORDER_PLACED",
  "orderId": "res-a1b2c3d4e5",
  "failureReason": null,
  "items": [
    { "productId": "prod-xyz789", "quantity": 2 }
  ],
  "amount": 99.98,
  "shippingAddress": "123 Main St, Springfield, IL 62701"
}
```

Fields not relevant to a given event type are `null`.

---

### kafka-zen.lab-802.saga.order.events

Published by: **order-service**

| `type` | Trigger | Non-null fields |
|---|---|---|
| `ORDER_PLACED` | New order placed | `eventId`, `orderId`, `items`, `amount`, `shippingAddress` |
| `ORDER_CANCELLATION_REQUESTED` | Manual cancel via REST | `eventId`, `orderId` |

**Example — ORDER_PLACED:**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "ORDER_PLACED",
  "orderId": "res-a1b2c3d4e5",
  "failureReason": null,
  "items": [{ "productId": "prod-xyz789", "quantity": 2 }],
  "amount": 99.98,
  "shippingAddress": "123 Main St, Springfield, IL 62701"
}
```

---

### kafka-zen.lab-802.saga.inventory.events

Published by: **inventory-service**

| `type` | Trigger | Non-null fields |
|---|---|---|
| `INVENTORY_RESERVED` | Reservation created | `eventId`, `orderId`, `amount`, `shippingAddress` (forwarded to payment-service) |
| `INVENTORY_RESERVATION_FAILED` | Quantity > 100 per item | `eventId`, `orderId`, `failureReason` |
| `INVENTORY_RELEASED` | Reservation released (compensation) | `eventId`, `orderId` |

**Example — INVENTORY_RESERVATION_FAILED:**

```json
{
  "eventId": "...",
  "type": "INVENTORY_RESERVATION_FAILED",
  "orderId": "res-a1b2c3d4e5",
  "failureReason": "Insufficient stock for product: prod-xyz789",
  "items": null,
  "amount": null,
  "shippingAddress": null
}
```

---

### kafka-zen.lab-802.saga.payment.events

Published by: **payment-service**

| `type` | Trigger | Non-null fields |
|---|---|---|
| `PAYMENT_PROCESSED` | Payment recorded | `eventId`, `orderId`, `shippingAddress` (forwarded to shipping-service) |
| `PAYMENT_FAILED` | Amount > 10,000 | `eventId`, `orderId`, `failureReason` |
| `PAYMENT_REFUNDED` | Refund after shipment failure | `eventId`, `orderId` |

---

### kafka-zen.lab-802.saga.shipping.events

Published by: **shipping-service**

| `type` | Trigger | Non-null fields |
|---|---|---|
| `SHIPMENT_CREATED` | Shipment record created | `eventId`, `orderId` |
| `SHIPMENT_FAILED` | Shipping address > 200 characters | `eventId`, `orderId`, `failureReason` |

---

### All Event Types

| `SagaEventType` | Publisher | Consumers |
|---|---|---|
| `ORDER_PLACED` | order-service | inventory-service |
| `ORDER_CANCELLATION_REQUESTED` | order-service | inventory-service, payment-service, shipping-service |
| `INVENTORY_RESERVED` | inventory-service | payment-service |
| `INVENTORY_RESERVATION_FAILED` | inventory-service | order-service |
| `INVENTORY_RELEASED` | inventory-service | order-service |
| `PAYMENT_PROCESSED` | payment-service | shipping-service |
| `PAYMENT_FAILED` | payment-service | inventory-service |
| `PAYMENT_REFUNDED` | payment-service | inventory-service |
| `SHIPMENT_CREATED` | shipping-service | order-service |
| `SHIPMENT_FAILED` | shipping-service | payment-service |

---

## Participant Service Endpoints

All participant services expose only the following endpoints:

| Endpoint | Method | Description |
|---|---|---|
| `/` | GET | Root health check — returns HTTP 200 |
| `/actuator/health` | GET | Spring Boot Actuator health |
| `/actuator/health/liveness` | GET | Kubernetes liveness probe |
| `/actuator/health/readiness` | GET | Kubernetes readiness probe |

| Service | Local port | Docker Compose port |
|---|---|---|
| order-service | 8080 | 8080 |
| inventory-service | 8080 | 8081 |
| payment-service | 8080 | 8082 |
| shipping-service | 8080 | 8084 |
| kafka-connect | — | 8083 |
