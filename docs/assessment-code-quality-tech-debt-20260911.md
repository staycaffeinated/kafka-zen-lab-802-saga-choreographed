# Code Quality & Technical Debt Assessment

**Project:** kafka-zen-lab-802-saga-choreographed
**Date:** 2026-09-11
**Audience:** Developer / Tech Lead
**Assessed by:** Claude Code (claude-sonnet-4-6)

---

## 1. Executive Summary

This codebase is the choreographed successor to the orchestrated `801` reference implementation. The central
`OrderSagaOrchestrator` and `SagaState` machine have been removed in favour of direct event reactions across four
independent Spring Boot microservices — `order-service`, `inventory-service`, `payment-service`, and
`shipping-service`. Each service publishes domain events via a **Transactional Outbox** pattern (JPA + Flyway table),
with **Debezium Postgres CDC** reading the WAL and routing to Kafka via the Outbox EventRouter SMT. The hexagonal
architecture, ArchUnit enforcement, shared common modules, and test pyramid from `801` are all preserved.

**Overall Score: 8.0 / 10**

### Key Findings

The architecture is sound and the transactional outbox eliminates the dual-write risk that was present in `801`
(finding H2). Several findings from `801` are carried forward in good shape: fail-fast `NOT_SET` env-var placeholders
are already in place, `@CreationTimestamp` is on domain entities, `gradle.properties` has parallel and caching settings,
and E2E tests address the end-to-end gap flagged in the `801` assessment. The remaining issues are fewer and less
severe than those in `801` at the equivalent stage.

Two medium-priority findings warrant near-term attention: `OutboxJpaEntity.createdAt` is set from the Java clock
rather than by Hibernate / the DB default (all four services), and `InventoryEventService.handleRelease` can throw
`IllegalStateException` if the same reservation is released twice, causing retries and DLQ routing on a valid
compensation edge case. A package-placement inconsistency in `shipping-service` escapes ArchUnit enforcement.

### Severity Matrix

| ID  | Title                                                                        | Severity | Status | Risk if Deferred |
|-----|------------------------------------------------------------------------------|----------|--------|------------------|
| H1  | `OutboxJpaEntity.createdAt` set from Java clock, not `@CreationTimestamp`   | High     | Resolved | Clock-skew across instances; Java overrides DB default |
| M1  | `shipping-service` config classes in `config.*` not `infrastructure.config.*` | Medium | Open   | ArchUnit naming rule does not cover these classes |
| M2  | `InventoryEventService.handleRelease` not idempotent for RELEASED reservations | Medium | Open  | Retry loop → DLQ on dual-release compensation edge case |
| M3  | `show-sql: true` in all four production `application.yml` files             | Medium   | Open   | SQL statements in production logs; data-shape exposure |
| M4  | `CancelOrderService` rejects cancel from non-CONFIRMING status              | Medium   | Open   | CONFIRMED orders cannot be cancelled via API |
| L1  | Placeholder copyright headers                                                | Low      | Open   | Legal ambiguity |
| L2  | `OrderStatus.PENDING` is dead code                                           | Low      | Open   | Misleading enum value; never set or checked |

---

## 2. Strengths to Preserve

**1. Transactional Outbox eliminates dual-write.**
Each `*EventPublisherAdapter` writes an `outbox` row inside the same `@Transactional` method as the domain state
change. Debezium reads from the Postgres WAL, not via polling, so no event is ever lost to an application crash
between the DB commit and the Kafka publish. This directly addresses the most significant production risk from `801`.

**2. No class-level `@Transactional`.**
Unlike the `OrderSagaOrchestrator` in `801` (finding H2), every application-layer method in `802` is individually
`@Transactional`. The transaction boundary is tight around the persistence call, not stretched across Kafka produce
calls.

**3. Clean hexagonal layering, enforced by ArchUnit.**
All four services maintain strict `domain → application → adapter → infrastructure` dependency direction with
`*ArchitectureTest.java` running on every build. Domain purity, no-field-injection, and naming conventions are all
validated at compile time.

**4. Fail-fast configuration defaults.**
`DB_URL:NOT_SET` and `KAFKA_BOOTSTRAP_SERVERS:NOT_SET` in all four `application.yml` files produce an unambiguous
startup failure rather than silently connecting to localhost in a misconfigured environment.

**5. Shared common modules.**
`common-adapter-components`, `common-application-components`, `common-domain-components`, and
`common-infra-components` centralise cross-cutting concerns — `SagaEvent`, `SagaEventType`, `GlobalExceptionHandler`,
validators, exception hierarchy, Jackson configuration, and `ResourceIdSupplierConfiguration` — across all four
services.

**6. Constructor injection throughout.**
Every `@Service`, `@Component`, and `@Configuration` bean uses constructor injection with final fields. No `@Autowired`
fields in any main source tree.

**7. MDC logging on all listener threads.**
All six Kafka listener classes inject `orderId` into the SLF4J MDC before processing and clear it in a `finally`
block, enabling correlated log search across all four services for any given saga.

**8. Idempotency on order placement.**
`PlaceOrderService` checks `IdempotencyKeyStore` before creating a new order. Duplicate submissions with the same
`Idempotency-Key` header return the original order without re-triggering the saga.

**9. DLQ with bounded retry.**
`KafkaConsumerConfiguration` in each service configures `DeadLetterPublishingRecoverer` with
`FixedBackOff(1_000L, 3L)`. `IllegalArgumentException` and `NullPointerException` are non-retryable and skip to DLQ
immediately.

**10. Test pyramid is respected.**
- Pure domain unit tests with no Spring context (`OrderTest`)
- Application-layer tests with Mockito (`PlaceOrderServiceTest`, `CancelOrderServiceTest`, `SagaEventHandlerTest`,
  `InventoryEventServiceTest`, `PaymentEventServiceTest`, `ShipmentEventServiceTest`)
- Kafka listener tests with mocked use cases (`OrderEventListenerTest`, `PaymentEventListenerTest`, etc.)
- Slice tests with `@WebMvcTest` (`OrderControllerTest`, `RootControllerTest`)
- Integration tests with Testcontainers Postgres
- E2E tests against the full Docker Compose stack (`OrderSagaE2ETest`)

**11. Debezium topic routing via `aggregate_type`.**
`OutboxJpaEntity.aggregateType` stores the topic name from `SagaTopicsProperties`. The Debezium EventRouter SMT routes
each outbox row to the correct topic without any additional configuration. `aggregateId` (orderId) serves as the
Kafka message key, guaranteeing partition affinity and in-order consumption per saga.

---

## 3. High Priority Issues

### H1 — `OutboxJpaEntity.createdAt` set from Java clock, not `@CreationTimestamp`

**Files (all four services):**
- `order-service/.../entity/OutboxJpaEntity.java` line 41, `OrderEventPublisherAdapter.java` line 72
- `inventory-service/.../entity/OutboxJpaEntity.java`, `InventoryEventPublisherAdapter.java` line 86
- `payment-service/.../entity/OutboxJpaEntity.java`, `PaymentEventPublisherAdapter.java` line 73
- `shipping-service/.../entity/OutboxJpaEntity.java`, `ShippingEventPublisherAdapter.java`

Each `*EventPublisherAdapter.persist()` call sets `createdAt(Instant.now())` in the builder. The outbox DDL already
declares `created_at timestamptz not null default now()`, so the DB default is there but JPA overrides it with the
Java application clock. In multi-instance deployments where JVM clocks differ by even a few milliseconds, outbox rows
from different instances will have inconsistent `created_at` values. The domain entity (`OrderJpaEntity`) was already
corrected in `801` with `@CreationTimestamp` — the same fix should be applied to all four `OutboxJpaEntity` classes.

**Recommended fix:** Add `@CreationTimestamp` to `OutboxJpaEntity.createdAt`, annotate the column with
`updatable = false`, and remove `createdAt(Instant.now())` from every `OutboxJpaEntity.builder()` call site:

```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;
```

---

## 4. Medium Priority Issues

### M1 — `shipping-service` `@Configuration` classes in `config.*` instead of `infrastructure.config.*`

**Files:**
- `shipping-service/src/main/java/zen/labs/shipping/config/KafkaConsumerConfiguration.java`
- `shipping-service/src/main/java/zen/labs/shipping/config/WebMvcConfiguration.java`

All other services place `@Configuration` classes under `*.infrastructure.config.*`. The `shipping-service` places
them under the root-level `zen.labs.shipping.config` package, which is outside all known architecture layers. The
ArchUnit `configuration_classes_must_be_suffixed_configuration` rule is scoped to
`zen.labs.shipping.infrastructure.config..` and therefore does not cover these two classes. They are unaudited by
ArchUnit.

`TopicProvisioner` is correctly placed at `zen.labs.shipping.infrastructure.config.TopicProvisioner`.

**Recommended fix:** Move both classes to `zen.labs.shipping.infrastructure.config.*`. Update the package declaration
and any `@Import` references.

---

### M2 — `InventoryEventService.handleRelease` not idempotent for already-RELEASED reservations

**File:** `inventory-service/.../service/InventoryEventService.java`, line 52.

```java
reservationDataStore
    .findByOrderId(orderId)
    .ifPresentOrElse(
        r -> {
            reservationDataStore.update(r.release()); // throws if status != RESERVED
            ...
        },
        () -> log.warn("No reservation to release: orderId={}", orderId));
eventPublisher.publishInventoryReleased(orderId); // not reached if above throws
```

`InventoryReservation.release()` throws `IllegalStateException` when `status != RESERVED`. The inventory-service has
two independent consumer groups (`inventory-service-order` and `inventory-service-payment`), both of which call
`handleRelease` for the same `orderId` — `OrderEventListener` processes `ORDER_CANCELLATION_REQUESTED` and
`PaymentEventListener` processes `PAYMENT_FAILED` or `PAYMENT_REFUNDED`. If the reservation is already in RELEASED
status when the second `handleRelease` call arrives, `r.release()` throws, causing the transaction to roll back. The
`publishInventoryReleased()` call at the bottom of the method is never reached, so no `INVENTORY_RELEASED` event is
written to the outbox for that invocation. The Kafka listener retries three times at 1-second intervals before routing
the message to DLQ.

In the normal happy-path cancel (order total < 10,000), only `ORDER_CANCELLATION_REQUESTED` triggers `handleRelease`,
so this does not surface. In the payment-failure compensation path (order total ≥ 10,000), both paths converge.

**Recommended fix:** Guard against the already-released case before calling `r.release()`:

```java
r -> {
    if (r.getStatus() == ReservationStatus.RELEASED) {
        log.info("Inventory already released (idempotent): orderId={}", orderId);
        return;
    }
    reservationDataStore.update(r.release());
    log.info("Inventory released: orderId={}", orderId);
}
```

---

### M3 — `show-sql: true` in all four production `application.yml` files

**Files:** all four `*/src/main/resources/application.yml`, `spring.jpa.show-sql: true`.

In a production deployment this setting floods application logs with every SQL statement executed by Hibernate,
including parameter values. This creates observability noise and may expose table/column structure in aggregated log
systems. It is appropriate in development profiles but should not be active in the main config.

**Recommended fix:** Set `show-sql: false` in each main `application.yml`. If SQL logging is needed during
development, override to `true` in `src/*/resources/application-local.yml` or a test profile.

---

### M4 — `CancelOrderService` rejects cancellation for non-CONFIRMING orders

**File:** `order-service/.../service/CancelOrderService.java`, line 27.

```java
if (!order.isConfirming()) {
    throw new UnprocessableRequestException("Order cannot be cancelled in status: " + order.getStatus());
}
```

An order can only be cancelled via the API while in `CONFIRMING` status. A `CONFIRMED` order (saga completed
successfully) cannot be cancelled — the REST endpoint returns 422. This is intentional for the lab scope but
represents a hard production limitation: there is no post-confirmation cancellation path. For a production system,
consider adding a `CONFIRMED → CANCELLING` transition with a dedicated compensation chain that reverses the
shipment and payment.

---

## 5. Low Priority Issues

### L1 — Copyright header contains placeholder `[CopyrightOwner]`

Multiple source files include `/* Copyright 2026 [CopyrightOwner] */`. Decide on a license and populate uniformly,
or remove entirely.

---

### L2 — `OrderStatus.PENDING` is dead code

**File:** `order-service/.../domain/OrderStatus.java`, line 7.

The `PENDING` enum constant is declared but never set or referenced anywhere in production code.
`PlaceOrderService` creates every new order with `OrderStatus.CONFIRMING`. Remove `PENDING` to avoid misleading
readers about the saga state machine, or document the intended state transition if it will be used in future.

---

## 6. Production Readiness Gaps

The following are not code defects but represent work required before this implementation can serve as a production
template.

1. **E2E test infrastructure fragility.** The Docker Compose stack marks all four microservices with
   `platform: linux/arm64`. On x86 hosts this engages QEMU user-space emulation, significantly increasing container
   startup and Debezium CDC propagation latency. The `SAGA_TIMEOUT` in `OrderSagaE2ETest` was increased to 60 s to
   compensate; on native ARM64 (Apple Silicon) the default 30 s was sufficient. Consider providing a second compose
   profile with `linux/amd64` images for CI on x86 agents.

2. **Debezium connector registration race.** The `connector-registrar` service uses `restart: on-failure` and has no
   `depends_on` ordering relative to the four microservices. If it registers connectors before the outbox tables exist
   (microservices still starting), Debezium will fail to resolve the table and the connector will error. Add
   `depends_on` conditions for the microservice health-check probes, or implement a retry loop in the registrar
   script.

3. **No distributed tracing.** Actuator alone does not instrument HTTP or Kafka spans. Add
   `micrometer-tracing-bridge-otel` to all four services to produce correlated trace IDs across the full choreography
   chain. The existing MDC `orderId` fields are a useful partial substitute but do not integrate with tracing
   backends.

4. **No security layer.** No Spring Security, no API authentication (JWT or mTLS), no CORS restrictions. The
   `order-service` REST API is fully open.

5. **No DLQ consumer or alerting.** Poison messages route to `<topic>.dlq` but no consumer or alert exists to act on
   them. Dead-letter records accumulate silently, including legitimate compensation-path double-releases (see M2).

6. **No saga timeout / recovery.** If a participant service is permanently unavailable the saga remains in
   `CONFIRMING` or `CANCELLING` status indefinitely. A timeout-based recovery job or saga expiry policy is needed for
   production use.

7. **Actuator exposure minimal.** Only `health` and `info` are exposed. For production operations, add `metrics` and
   `prometheus`, and restrict the management port to an internal network.

8. **Outbox rows never deleted.** By design (lab context), outbox rows accumulate indefinitely. Before production use,
   add a cleanup job or Debezium `signal.data.collection` tombstone strategy to bound table growth.

---

## 7. Recommendations & Roadmap

### Recommended Action Order

Resolve H1 across all four services in one pass. M2 and M3 can be addressed in the same sprint. M1 is a mechanical
package move. M4 is a product decision before production use. Low-priority items are cleanup.

### Roadmap

| Sprint | ID  | Action                                                                                                              | Owner       | Status    |
|--------|-----|---------------------------------------------------------------------------------------------------------------------|-------------|-----------|
| 1      | H1  | Add `@CreationTimestamp` to all four `OutboxJpaEntity.createdAt`; remove `createdAt(Instant.now())` from all four `*EventPublisherAdapter.persist()` calls | Backend dev | ✅ Done |
| 1      | M2  | Guard `InventoryEventService.handleRelease` against already-RELEASED status before calling `r.release()`          | Backend dev | ⏳ Open   |
| 1      | M3  | Set `show-sql: false` in all four main `application.yml` files                                                      | Backend dev | ⏳ Open   |
| 2      | M1  | Move `shipping-service` `KafkaConsumerConfiguration` and `WebMvcConfiguration` to `infrastructure.config` package  | Backend dev | ⏳ Open   |
| 2      | L2  | Remove `OrderStatus.PENDING` or document its intended use                                                           | Backend dev | ⏳ Open   |
| —      | M4  | Design post-confirmation cancellation path (product decision required)                                              | Tech lead   | ⏳ Open   |
| —      | L1  | Decide on license; populate or remove copyright headers uniformly                                                   | Tech lead   | ⏳ Open   |
| —      | Gap | Add `depends_on` health-check ordering to `connector-registrar` in Docker Compose                                   | Backend dev | ⏳ Open   |
| —      | Gap | Provide `linux/amd64` compose profile for E2E tests on x86 CI agents                                               | Backend dev | ⏳ Open   |
| —      | Gap | Add `micrometer-tracing-bridge-otel` for distributed tracing across all four services                               | Backend dev | ⏳ Open   |
| —      | Gap | Add saga timeout / expiry recovery job                                                                              | Backend dev | ⏳ Open   |
| —      | Gap | Implement Spring Security with JWT or mTLS on `order-service`                                                       | Backend dev | ⏳ Open   |
| —      | Gap | Expose `metrics` and `prometheus` actuator endpoints; restrict management port                                       | Backend dev | ⏳ Open   |
| —      | Gap | Implement DLQ consumer with alerting                                                                                | Backend dev | ⏳ Open   |
| —      | Gap | Implement outbox row cleanup (TTL job or Debezium tombstone strategy)                                               | Backend dev | ⏳ Open   |

### Summary

The codebase scores 8.0/10. The transactional outbox is the most significant structural improvement over `801`:
dual-write is eliminated, and the `@Transactional` span covers only the database write, not a downstream Kafka
publish. The test pyramid is complete including E2E coverage. The findings are fewer and less severe than those in
`801` at an equivalent stage. H1 (`OutboxJpaEntity.createdAt`) is the single most impactful fix — it is a four-service
change requiring less than 30 minutes total — and should be done first.
