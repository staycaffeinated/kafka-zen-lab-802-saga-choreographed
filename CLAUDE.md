# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```shell
# Build (compiles + unit tests + integration tests)
./gradlew build

# Unit tests only
./gradlew test

# Integration tests only (uses TestContainers — requires Docker)
./gradlew integrationTest

# Single subproject tests
./gradlew :order-service:test
./gradlew :order-service:integrationTest

# Single test class
./gradlew :order-service:test --tests "zen.labs.order.OrderServiceArchitectureTest"

# Build with SonarQube coverage (configure gradle.properties first)
./gradlew clean build sonar

# Build Docker image (Jib, no daemon required)
./gradlew jibDockerBuild

# Run in Docker (after jibDockerBuild)
cd docker && docker-compose up -d

# Check for dependency updates
./gradlew dependencyUpdates
```

Java version is pinned in `.java-version` (default: 17). Change the number in that file to switch versions — Gradle toolchain picks it up automatically.

## Architecture

This is a **Saga pattern** implementation over Kafka. The project name says "choreographed" (`802`) and the branch `feat/migrate-to-choreographed` indicates active migration from the orchestrated variant (`801`). The current code still contains orchestration logic in `order-service`; the choreographed pattern is being built out.

### Multi-module layout

Four **microservices** (each is a runnable Spring Boot app):
- `order-service` — saga orchestrator; exposes REST API, drives saga steps
- `inventory-service` — saga participant; reserves/releases inventory
- `payment-service` — saga participant; processes/refunds payments
- `shipping-service` — saga participant; creates shipments

Four **shared libraries** (not runnable):
- `common-adapter-components` — Kafka topic constants (`KafkaTopics`), saga command/reply enums, shared validators, `GlobalExceptionHandler`, `CorrelationIdFilter`
- `common-application-components` — shared exception types, `ResourceIdSupplier` port
- `common-domain-components` — `ResourceIdAware`, `ResourceIdFormat`
- `common-infra-components` — Jackson config, datetime config, `ResourceIdGenerator` bean

### Hexagonal (Ports & Adapters) layer structure

Each service follows strict layering, **enforced by ArchUnit** (`*ArchitectureTest`):

```
domain/          → pure Java; no Spring, no other layers
application/
  port/inbound/  → use-case interfaces (e.g., PlaceOrderUseCase)
  port/outbound/ → data-store / publisher interfaces (e.g., OrderDataStore)
  service/       → use-case implementations; depend only on ports + domain
adapter/
  inbound/       → REST controllers, Kafka consumers (implement inbound ports)
  outbound/      → JPA data stores, Kafka publishers (implement outbound ports)
infrastructure/
  config/        → Spring @Configuration classes (Kafka, WebMvc, topic properties)
  advice/        → exception handlers scoped to this service
```

Dependency rules (violations fail unit tests):
- `domain` → nothing
- `application` → domain only
- `adapter` → no `infrastructure`
- `infrastructure` → no `adapter`
- No `@Autowired` field injection anywhere

### Kafka topic model

Defined in `common-adapter-components/.../kafka/KafkaTopics.java`:

| Topic | Direction |
|---|---|
| `kafka-zen.lab-801.saga.*.commands` | Orchestrator → participant |
| `kafka-zen.lab-801.saga.orchestrator.replies` | All participants → orchestrator |

`SagaCommandType` and `SagaReplyType` enums (same package) enumerate all message types.

### Saga flow (current orchestrated implementation)

`OrderSagaOrchestrator` drives the saga:

1. `RESERVE_INVENTORY` → inventory-service  
2. On success: `PROCESS_PAYMENT` → payment-service  
3. On success: `CREATE_SHIPMENT` → shipping-service  
4. Compensating path on any failure: refund payment → release inventory

`SagaState` (persisted in Postgres) tracks `SagaStatus` × `SagaStep` for each in-flight saga. `IdempotencyKeyStore` prevents duplicate order creation.

### Testing conventions

- Unit tests: `src/test/java` — use Mockito, no Spring context unless `@WebMvcTest`/`@DataJpaTest`
- Integration tests: `src/integrationTest/java` — use TestContainers (Postgres + Kafka); extend `AbstractPostgresIntegrationTest`
- Architecture tests: `*ArchitectureTest` in unit-test source set; use ArchUnit `@ArchTest`
- `check` task runs both unit and integration tests plus coverage reports

### Gradle conventions

Convention plugins live in `gradle/plugins/java-conventions/src/main/groovy/`:
- `buildlogic.application-conventions` — applied by all 4 services
- `buildlogic.library-conventions` — applied by all 4 common modules
- `buildlogic.integration-test` — wires the `integrationTest` JVM test suite
- `buildlogic.jacoco` — coverage; `buildlogic.sonar-jacoco` — Sonar integration
- `buildlogic.spotless` — code formatting
- `buildlogic.lint` — Nebula lint

Dependency versions are centralized in `gradle/libs.versions.toml`.
