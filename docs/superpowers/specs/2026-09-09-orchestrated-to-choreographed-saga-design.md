# Design: Orchestrated → Choreographed Saga Refactor

**Date:** 2026-09-09  
**Branch:** feat/migrate-to-choreographed  
**Scope:** All 8 modules (4 microservices + 4 shared libraries)

---

## Context

The codebase currently implements a **Saga orchestration** pattern: `OrderSagaOrchestrator` in `order-service` drives every saga step by sending Kafka commands to participants and reacting to their replies on a central reply topic.

This refactor converts to **Saga choreography**: services react to domain events published by other services. No central coordinator. No saga state tracked in a database.

---

## Decisions

| Question | Decision |
|---|---|
| Keep saga state tracking? | No — drop entirely |
| Topic layout | One topic per service (4 topics) |
| Cancellation | Full choreographed: ORDER_CANCELLATION_REQUESTED; each participant compensates independently |

---

## Event Model

### Topics

| Topic | Owner |
|---|---|
| `kafka-zen.lab-802.saga.order.events` | order-service |
| `kafka-zen.lab-802.saga.inventory.events` | inventory-service |
| `kafka-zen.lab-802.saga.payment.events` | payment-service |
| `kafka-zen.lab-802.saga.shipping.events` | shipping-service |

### Event Envelope

```java
record SagaEvent(String eventId, SagaEventType type, String orderId, String failureReason)
```

`eventId` is a new UUID per event (consumer idempotency key). `failureReason` is null on success events.

### Event Types (`SagaEventType` enum)

| Publisher | Event |
|---|---|
| order-service | `ORDER_PLACED` |
| order-service | `ORDER_CANCELLATION_REQUESTED` |
| inventory-service | `INVENTORY_RESERVED` |
| inventory-service | `INVENTORY_RESERVATION_FAILED` |
| inventory-service | `INVENTORY_RELEASED` |
| payment-service | `PAYMENT_PROCESSED` |
| payment-service | `PAYMENT_FAILED` |
| payment-service | `PAYMENT_REFUNDED` |
| shipping-service | `SHIPMENT_CREATED` |
| shipping-service | `SHIPMENT_FAILED` |

---

## Service Responsibilities

### order-service

Publishes to `order-events`. Subscribes to `inventory-events`, `payment-events`, `shipping-events`.

| Incoming event | Action |
|---|---|
| `INVENTORY_RESERVATION_FAILED` | mark order `CANCELLED` |
| `INVENTORY_RELEASED` | mark order `CANCELLED` |
| `SHIPMENT_CREATED` | mark order `CONFIRMED` |

### inventory-service

Publishes to `inventory-events`. Subscribes to `order-events`, `payment-events`.

| Incoming event | Action |
|---|---|
| `ORDER_PLACED` | reserve inventory → publish `INVENTORY_RESERVED` or `INVENTORY_RESERVATION_FAILED` |
| `ORDER_CANCELLATION_REQUESTED` | release if reserved → always publish `INVENTORY_RELEASED` |
| `PAYMENT_FAILED` | release inventory → publish `INVENTORY_RELEASED` |
| `PAYMENT_REFUNDED` | release inventory → publish `INVENTORY_RELEASED` |

### payment-service

Publishes to `payment-events`. Subscribes to `inventory-events`, `shipping-events`, `order-events`.

| Incoming event | Action |
|---|---|
| `INVENTORY_RESERVED` | process payment → publish `PAYMENT_PROCESSED` or `PAYMENT_FAILED` |
| `SHIPMENT_FAILED` | refund payment → publish `PAYMENT_REFUNDED` |
| `ORDER_CANCELLATION_REQUESTED` | refund if payment exists (no compensating event — inventory owns terminal signal) |

### shipping-service

Publishes to `shipping-events`. Subscribes to `payment-events`, `order-events`.

| Incoming event | Action |
|---|---|
| `PAYMENT_PROCESSED` | create shipment → publish `SHIPMENT_CREATED` or `SHIPMENT_FAILED` |
| `ORDER_CANCELLATION_REQUESTED` | cancel shipment if exists (no compensating event needed) |

---

## Compensation Chains

```
Inventory fails:
  ORDER_PLACED → INVENTORY_RESERVATION_FAILED
  → order-service marks CANCELLED

Payment fails:
  INVENTORY_RESERVED → PAYMENT_FAILED
  → inventory releases → INVENTORY_RELEASED
  → order-service marks CANCELLED

Shipment fails:
  PAYMENT_PROCESSED → SHIPMENT_FAILED
  → payment refunds → PAYMENT_REFUNDED
  → inventory releases → INVENTORY_RELEASED
  → order-service marks CANCELLED

User cancels (order must be in CONFIRMING status):
  ORDER_CANCELLATION_REQUESTED
  → inventory compensates (always) → INVENTORY_RELEASED → order-service marks CANCELLED
  → payment compensates if payment exists (no event)
  → shipping compensates if shipment exists (no event)
```

`INVENTORY_RELEASED` is always the terminal signal for order-service in all failure/cancel paths.

---

## Code Changes

### `common-adapter-components`

**Delete:**
- `SagaCommandType.java`
- `SagaReplyType.java`
- `KafkaTopics.java` (5 old topics)

**Add:**
- `SagaEventType.java` — enum with all 10 event types
- `KafkaTopics.java` — 4 new event topic constants
- `SagaEvent.java` — shared event envelope record

---

### `order-service`

**Delete:**

| Layer | Classes |
|---|---|
| Domain | `SagaState`, `SagaStatus`, `SagaStep`, `SagaDomainException` |
| App ports inbound | `HandleSagaReplyUseCase`, `FindSagaUseCase`, `CancelOrderUseCase` |
| App ports outbound | `SagaStateDataStore`, `SagaCommandPublisher` |
| App services | `OrderSagaOrchestrator`, `SagaQueryService` |
| Adapter inbound | `SagaReplyListener`, `SagaReply` (inbound DTO) |
| Adapter outbound | `KafkaCommandPublisher`, `SagaCommand`, `SagaCommandItem` (outbound DTOs) |
| Adapter inbound web | `SagaController`, `SagaStateResponse` |
| Persistence | `SagaStateJpaEntity`, `SagaStateRepository`, `SagaStateJpaDataStore` |

**Add:**

| Layer | Classes |
|---|---|
| App ports inbound | `CancelOrderUseCase` (new, simpler interface) |
| App ports outbound | `OrderEventPublisher` (publishOrderPlaced, publishCancellationRequested) |
| App services | `PlaceOrderService` (save order → publish ORDER_PLACED) |
| App services | `CancelOrderService` (check CONFIRMING → mark CANCELLING → publish ORDER_CANCELLATION_REQUESTED) |
| App services | `SagaEventHandler` (update order status from incoming events) |
| Adapter inbound | `SagaEventListener` (Kafka consumer: inventory + payment + shipping event topics) |
| Adapter outbound | `KafkaOrderEventPublisher` (implements OrderEventPublisher) |

**Modify:**
- `OrderController` — remove `GET /sagas` and `GET /sagas/{id}` endpoints; keep `POST /orders/{id}/cancel` wired to `CancelOrderService`
- `KafkaConsumerConfiguration` — subscribe to `inventory-events`, `payment-events`, `shipping-events`
- Flyway migrations — add migration to drop `saga_state` table
- `OrderServiceArchitectureTest` — remove any rules referencing deleted packages

---

### `inventory-service`, `payment-service`, `shipping-service`

Same structural pattern for each:

**Delete:**
- `SagaCommandListener` (inbound adapter)
- `SagaCommand`, `SagaCommandItem` (inbound DTOs)
- `KafkaReplyPublisher`, `SagaReply` (outbound adapter + DTO)
- `HandleXxxCommandUseCase` (inbound port)
- `KafkaConsumerConfiguration` (old command topic wiring)

**Add:**
- `HandleXxxEventUseCase` (one or more inbound port interfaces, per event type handled)
- `*EventListener` (Kafka consumer adapter, per source topic subscribed)
- `XxxEventPublisher` (outbound port interface)
- `KafkaXxxEventPublisher` (outbound adapter implementing above)
- `XxxEventHandlerService` (app service implementing use cases)
- `KafkaConsumerConfiguration` (new event topic wiring)

---

## Implementation Order

Work module by module, keeping the build green between each step:

1. `common-adapter-components` — new topics + event types (compile breaks across all services; fix immediately)
2. `order-service` — gut orchestrator, add event publisher + listener
3. `inventory-service`
4. `payment-service`
5. `shipping-service`

---

## Testing

### ArchUnit
No structural rule changes needed. Layer rules remain valid. Update any rules that reference deleted package names.

### Unit Tests
Delete tests for all removed classes. Add:
- `PlaceOrderServiceTest` — verify order saved + `ORDER_PLACED` published
- `CancelOrderServiceTest` — verify status guard + `ORDER_CANCELLATION_REQUESTED` published; reject non-CONFIRMING
- `SagaEventHandlerTest` — one test per incoming event type; verify order status transition

### Integration Tests
Update each `*IntegrationTest`:
- `OrderServiceIntegrationTest` — place order → assert `ORDER_PLACED` on `order-events`; publish `SHIPMENT_CREATED` → assert order CONFIRMED
- `InventoryServiceIntegrationTest` — publish `ORDER_PLACED` → assert `INVENTORY_RESERVED`; publish `PAYMENT_FAILED` → assert `INVENTORY_RELEASED`
- Same pattern for payment and shipping

All use existing TestContainers (Postgres + Kafka) — no infrastructure changes.

End-to-end cross-service testing (docker-compose) is out of scope for this refactor.
