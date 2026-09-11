# Development Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 17 | Pinned in `.java-version`. Gradle toolchain support downloads a matching JDK automatically. |
| Gradle | Gradle wrapper (`gradlew`) provided | No local Gradle installation required. |
| Docker | Any recent version | Required for PostgreSQL, Kafka, Debezium Connect, and `jibDockerBuild`. |

---

## Environment Setup

```bash
# Verify Java version
java -version   # should show 17 or higher

# Make the wrapper executable (first time on macOS/Linux)
chmod +x ./gradlew

# Confirm the build works (compiles, formats, runs all tests)
./gradlew build
```

The `.java-version` file at the project root controls the toolchain version. Gradle provisions the correct JDK
automatically via the Foojay Toolchain Resolver.

---

## Running the Infrastructure

A single Docker Compose file is provided in the `docker/` directory. It starts PostgreSQL (with `wal_level=logical`),
Kafka (Zookeeper mode), Debezium Kafka Connect, and a one-shot `connector-registrar` container that registers the
four Debezium outbox connectors at startup.

### Start infrastructure only (run services from your IDE)

```bash
docker compose -f docker/docker-compose.yml up -d postgres zookeeper kafka connect connector-registrar
```

Wait for `connector-registrar` to exit (it runs once and terminates after posting the connector configs to the
Connect REST API). Then start the services from your IDE or with `bootRun`.

### Start full stack (all services as Docker containers)

Requires Docker images to be built first — see [Building Docker Images](#building-docker-images).

```bash
docker compose -f docker/docker-compose.yml up -d
```

---

## Running the Services

```bash
./gradlew :order-service:bootRun
./gradlew :inventory-service:bootRun
./gradlew :payment-service:bootRun
./gradlew :shipping-service:bootRun
```

Default service ports:

| Service | Local port | Docker Compose port |
|---|---|---|
| order-service | 8080 | 8080 |
| inventory-service | 8080 | 8081 |
| payment-service | 8080 | 8082 |
| shipping-service | 8080 | 8084 |
| kafka-connect | — | 8083 |
| kafka | — | 9092 |
| postgres | — | 5432 |

Note: shipping-service uses Docker Compose port 8084 (not 8083) to avoid conflict with kafka-connect.

---

## Build Tasks

```bash
# Compile, apply formatting, run all unit + integration tests, generate coverage
./gradlew build

# Unit tests only (all modules)
./gradlew test

# Integration tests only (requires Docker — Testcontainers)
./gradlew integrationTest

# Single subproject tests
./gradlew :order-service:test
./gradlew :order-service:integrationTest

# Single test class
./gradlew :order-service:test --tests "zen.labs.order.OrderServiceArchitectureTest"

# Apply Spotless code formatting
./gradlew spotlessApply

# Check formatting without modifying files
./gradlew spotlessCheck

# Check for outdated dependencies
./gradlew dependencyUpdates

# Export coverage to SonarQube
./gradlew clean build sonar

# Generate aggregated JaCoCo coverage reports
./gradlew testCodeCoverageReport integrationTestCodeCoverageReport
```

### Building Docker Images

```bash
./gradlew :order-service:jibDockerBuild
./gradlew :inventory-service:jibDockerBuild
./gradlew :payment-service:jibDockerBuild
./gradlew :shipping-service:jibDockerBuild
```

Jib builds images using `eclipse-temurin` as the base image. No Docker daemon required for the build itself (only
for `jibDockerBuild` which loads into the local Docker daemon).

### Task Dependency Graph

```
check
 ├── test
 ├── integrationTest
 ├── spotlessCheck
 ├── testCodeCoverageReport
 └── integrationTestCodeCoverageReport
```

---

## Project Structure

```
kafka-zen-lab-802-saga-choreographed/       Root project
├── build.gradle                             Root: JaCoCo aggregation, Sonar, Nebula lint
├── settings.gradle
├── gradle.properties
├── .java-version                            Java toolchain version pin
├── gradle/
│   ├── libs.versions.toml                   Version catalog
│   └── plugins/java-conventions/            Convention plugins (buildlogic.*)
├── common-adapter-components/
│   └── src/main/java/zen/labs/adapters/common/
│       ├── filters/                         CorrelationIdFilter, LoggingFilter
│       ├── handlers/                        GlobalExceptionHandler
│       ├── kafka/                           SagaEvent, SagaEventType, SagaEventItem, KafkaTopics
│       └── validation/                      @Alphabetic, @ResourceId, @SearchText
├── common-application-components/
│   └── src/main/java/zen/labs/application/common/
│       ├── exceptions/                      ApplicationException, BadRequestException, ResourceNotFoundException, UnprocessableRequestException
│       └── port/outbound/                   ResourceIdSupplier
├── common-domain-components/
│   └── src/main/java/zen/labs/domain/common/
│       └── ResourceIdAware, ResourceIdFormat
├── common-infra-components/
│   └── src/main/java/zen/labs/infrastructure/
│       ├── config/                          JacksonConfiguration, DateTimeFormatConfiguration, LocalDateConverter,
│       │                                    ResourceIdSupplierConfiguration, SagaTopicsProperties
│       ├── profiles/                        SpringProfiles
│       └── utils/                           ResourceIdGenerator
├── docker/
│   ├── docker-compose.yml
│   └── connectors/                          order-, inventory-, payment-, shipping-outbox-connector.json
├── order-service/
│   └── src/
│       ├── main/java/zen/labs/order/        Source (domain, application, adapter, infrastructure)
│       ├── main/resources/                  application.yml, db/migration/ (V0001–V0007)
│       ├── test/java/                       Unit tests + OrderServiceArchitectureTest
│       └── integrationTest/java/            Integration tests (Testcontainers)
├── inventory-service/                       db/migration/ V0001–V0004
├── payment-service/                         db/migration/ V0001–V0004
└── shipping-service/                        db/migration/ V0001–V0004
```

---

## Testing Strategy

### Tier 1: Unit Tests (`src/test/`)

Fast tests that run without Spring context or external infrastructure.

#### order-service

| Test class | What it tests |
|---|---|
| `OrderServiceArchitectureTest` | ArchUnit layer rules, naming conventions, no field injection, domain Spring-purity |
| `PlaceOrderServiceTest` | Order persistence, outbox event written, idempotency key check |
| `CancelOrderServiceTest` | `ORDER_CANCELLATION_REQUESTED` outbox event written |
| `SagaEventHandlerTest` | `onInventoryReservationFailed`, `onInventoryReleased`, `onShipmentCreated` — order status transitions |
| `OrderQueryServiceTest` | `findOrder` and `listOrdersByCustomer` delegation |
| `SagaEventListenerTest` | Kafka listener dispatch — all event types, exception propagation, no ack on failure |
| `OrderControllerTest` | `@WebMvcTest` slice — HTTP binding, request validation, response serialisation |
| `OrderEventPublisherAdapterTest` | Outbox row written with correct fields for each event type |
| `OrderJpaDataStoreTest` | JPA entity ↔ domain model conversion and CRUD |
| `TopicProvisionerTest` | Topic creation on `ApplicationReadyEvent` |
| `RootControllerTest` | `GET /` returns HTTP 200 |

#### inventory-service

| Test class | What it tests |
|---|---|
| `InventoryServiceArchitectureTest` | ArchUnit rules |
| `InventoryEventServiceTest` | `handleOrderPlaced` (success + quantity failure), `handleRelease` |
| `OrderEventListenerTest` | `ORDER_PLACED` and `ORDER_CANCELLATION_REQUESTED` dispatch, exception propagation |
| `PaymentEventListenerTest` | `PAYMENT_FAILED` and `PAYMENT_REFUNDED` → release inventory |
| `InventoryEventPublisherAdapterTest` | Outbox row written for each event type |
| `InventoryReservationJpaDataStoreTest` | JPA CRUD and domain conversion |
| `TopicProvisionerTest` | Topic creation on startup |

#### payment-service and shipping-service

Mirror the inventory-service structure with domain-specific tests (`PaymentEventServiceTest`,
`ShipmentEventServiceTest`) and listener tests for the events each service subscribes to.

#### common-adapter-components

| Test class | What it tests |
|---|---|
| `GlobalExceptionHandlerTest` | Exception-to-HTTP-status mapping and RFC-7807 body shape |
| `AlphabeticValidatorTest` | Custom `@Alphabetic` constraint |
| `ResourceIdValidatorTest` | Custom `@ResourceId` constraint |
| `SearchTextValidatorTest` | Custom `@SearchText` constraint |

### Tier 2: Integration Tests (`src/integrationTest/`)

Full `@SpringBootTest` on a random port with Testcontainers-managed PostgreSQL and embedded Kafka. Active profiles:
`test` + `integration-test`.

| Module | Focus area |
|---|---|
| order-service | Application context load, `GET /` returns HTTP 200, RFC-7807 error body shape |
| inventory-service | Application context load |
| payment-service | Application context load |
| shipping-service | Application context load |

Integration tests use `AbstractPostgresIntegrationTest` base classes. The database schema is initialised by a
`01-create-schema.sql` script before Flyway migrations run. The outbox table is present but Debezium is not active
in integration tests — integration tests verify that outbox rows are written with correct content, not that Kafka
delivery occurred.

---

## Verifying the Debezium Setup

After starting the full Docker stack, confirm the connectors are registered and running:

```bash
# List registered connectors
curl -s http://localhost:8083/connectors | jq .

# Check connector status
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq .

# Expected: {"connector":{"state":"RUNNING",...},"tasks":[{"state":"RUNNING",...}]}
```

Verify that PostgreSQL has `wal_level=logical`:

```bash
docker exec kafka-zen-lab-802_dbms psql -U postgres -c "SHOW wal_level;"
# Expected: wal_level = logical
```

---

## Coding Conventions

### Adding a New Saga Event Type

1. Add the event name to `SagaEventType` in `common-adapter-components`.
2. Add the publish method to the relevant `*EventPublisher` outbound port.
3. Implement the method in the `*EventPublisherAdapter` — write a row to the `outbox` table with the correct
   `aggregate_type` (= Kafka topic name), `aggregate_id` (= `orderId`), `type`, and `payload`.
4. Add the listener `case` (or `if` check) in the relevant `*EventListener` adapters of subscribing services.
5. Implement the handler method in the subscribing service's application service.

### Adding a New Kafka Topic

1. Add the topic name constant to `KafkaTopics` in `common-adapter-components`.
2. Add the corresponding property to `SagaTopicsProperties` in `common-infra-components`.
3. Reference the property in each service's `application.yml`.
4. Add the constant to `TopicProvisioner` in each service that needs it created at startup.
5. Add a new Debezium connector JSON in `docker/connectors/` if a new outbox table is involved.

### Domain Layer Rules

The `domain` package must not import from Spring, JPA, Kafka, infrastructure, adapter, or application packages.
ArchUnit enforces this at build time. Use plain Java classes and records only.

### Application Layer Rules

Application services must not import from `adapter` or `infrastructure`. Outbound ports in
`application.port.outbound` are implemented by adapters. The application layer depends only on the domain.

### Outbox Publisher Placement

`*EventPublisherAdapter` classes live in `adapter/outbound/persistence/` — they write to the database, not to Kafka.
This keeps the ArchUnit layer rules satisfied and makes the persistence nature of event publishing explicit.

### Exception Handling

Custom exceptions extend `RuntimeException` and live in `common-application-components`. `GlobalExceptionHandler`
in `common-adapter-components` maps them to HTTP status codes and RFC-7807 bodies.

| Exception | HTTP status |
|---|---|
| `BadRequestException` | 400 |
| `ResourceNotFoundException` | 404 |
| `UnprocessableRequestException` | 422 |

Kafka listener exceptions propagate to the Spring Kafka error handler (no try/catch swallowing them). The
`DefaultErrorHandler` retries up to 3 times, then routes to the DLQ topic.

### Code Formatting

Spotless enforces Palantir Java Format. The check runs automatically as part of `build`. Before committing:

```bash
./gradlew spotlessApply
```

---

## SonarQube Integration

```bash
# Start a local SonarQube container
docker run -d --name sonarqube \
  -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
  -p 9000:9000 sonarqube:latest
```

Update `gradle.properties` with your SonarQube host, token, and project key, then:

```bash
./gradlew clean build sonar
```

Coverage data from both `test` and `integrationTest` suites is aggregated via `jacoco-report-aggregation` in the
root `build.gradle`. Each service module defines its own `sonar.projectKey` and exclusion list in its `build.gradle`.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_USERNAME` | `postgres` | PostgreSQL username |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/postgres` | PostgreSQL JDBC URL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
