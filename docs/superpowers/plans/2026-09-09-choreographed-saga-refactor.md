# Choreographed Saga Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the centrally-orchestrated Kafka saga (OrderSagaOrchestrator driving participants via command/reply topics) with a choreographed saga where each service reacts to domain events published by other services.

**Architecture:** Four service-owned event topics replace five command/reply topics. Each service publishes events to its own topic and subscribes to relevant topics from other services. The order-service no longer drives the saga—it only publishes ORDER_PLACED, listens for terminal events, and updates order status accordingly.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring Kafka, JUnit 5, Mockito, Flyway, TestContainers, AssertJ

---

## Build command reference

```bash
# compile + unit + integration tests (all modules)
./gradlew build

# single module
./gradlew :order-service:build

# unit tests only
./gradlew :order-service:test

# integration tests only (needs Docker)
./gradlew :order-service:integrationTest

# single test class
./gradlew :order-service:test --tests "zen.labs.order.application.service.PlaceOrderServiceTest"
```

---

## Task 1: Rebuild common-adapter-components event model

> After this task the project will NOT compile until Tasks 2-3 fix order-service. Complete Tasks 1-3 before running `./gradlew build`.

**Files:**
- Delete: `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaCommandType.java`
- Delete: `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaReplyType.java`
- Modify: `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/KafkaTopics.java`
- Create: `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaEventType.java`
- Create: `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaEventItem.java`
- Create: `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaEvent.java`

- [ ] **Step 1.1: Delete old enum files**

```bash
rm common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaCommandType.java
rm common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaReplyType.java
```

- [ ] **Step 1.2: Rewrite KafkaTopics.java with new event topics**

Replace the entire content of `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/KafkaTopics.java`:

```java
package zen.labs.adapters.common.kafka;

public final class KafkaTopics {

    public static final String ORDER_EVENTS     = "kafka-zen.lab-802.saga.order.events";
    public static final String INVENTORY_EVENTS = "kafka-zen.lab-802.saga.inventory.events";
    public static final String PAYMENT_EVENTS   = "kafka-zen.lab-802.saga.payment.events";
    public static final String SHIPPING_EVENTS  = "kafka-zen.lab-802.saga.shipping.events";

    private KafkaTopics() {}
}
```

- [ ] **Step 1.3: Create SagaEventType.java**

Create `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaEventType.java`:

```java
package zen.labs.adapters.common.kafka;

public enum SagaEventType {
    // order-service publishes
    ORDER_PLACED,
    ORDER_CANCELLATION_REQUESTED,

    // inventory-service publishes
    INVENTORY_RESERVED,
    INVENTORY_RESERVATION_FAILED,
    INVENTORY_RELEASED,

    // payment-service publishes
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,

    // shipping-service publishes
    SHIPMENT_CREATED,
    SHIPMENT_FAILED
}
```

- [ ] **Step 1.4: Create SagaEventItem.java**

Create `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaEventItem.java`:

```java
package zen.labs.adapters.common.kafka;

public record SagaEventItem(String productId, int quantity) {}
```

- [ ] **Step 1.5: Create SagaEvent.java**

Create `common-adapter-components/src/main/java/zen/labs/adapters/common/kafka/SagaEvent.java`:

```java
package zen.labs.adapters.common.kafka;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shared envelope for all saga domain events.
 *
 * Fields are nullable — only populated when meaningful for the event type:
 *   ORDER_PLACED         → items, amount, shippingAddress
 *   INVENTORY_RESERVED   → amount (echoed for payment-service)
 *   PAYMENT_PROCESSED    → shippingAddress (echoed for shipping-service)
 *   *_FAILED             → failureReason
 *   all others           → orderId only
 */
public record SagaEvent(
        String eventId,
        SagaEventType type,
        String orderId,
        String failureReason,
        List<SagaEventItem> items,
        BigDecimal amount,
        String shippingAddress) {}
```

- [ ] **Step 1.6: Commit**

```bash
git add common-adapter-components/
git commit -m "refactor(common): replace command/reply enums with choreography event model"
```

---

## Task 2: Delete orchestration code from order-service

> Build remains broken after this task. Complete Task 3 immediately after.

**Files to delete:**

```
order-service/src/main/java/zen/labs/order/domain/SagaState.java
order-service/src/main/java/zen/labs/order/domain/SagaStatus.java
order-service/src/main/java/zen/labs/order/domain/SagaStep.java
order-service/src/main/java/zen/labs/order/domain/SagaDomainException.java
order-service/src/main/java/zen/labs/order/application/port/inbound/HandleSagaReplyUseCase.java
order-service/src/main/java/zen/labs/order/application/port/inbound/FindSagaUseCase.java
order-service/src/main/java/zen/labs/order/application/port/inbound/CancelOrderUseCase.java
order-service/src/main/java/zen/labs/order/application/port/outbound/SagaStateDataStore.java
order-service/src/main/java/zen/labs/order/application/port/outbound/SagaCommandPublisher.java
order-service/src/main/java/zen/labs/order/application/service/OrderSagaOrchestrator.java
order-service/src/main/java/zen/labs/order/application/service/SagaQueryService.java
order-service/src/main/java/zen/labs/order/adapter/inbound/messaging/SagaReplyListener.java
order-service/src/main/java/zen/labs/order/adapter/inbound/messaging/SagaReply.java
order-service/src/main/java/zen/labs/order/adapter/inbound/web/saga/SagaController.java
order-service/src/main/java/zen/labs/order/adapter/inbound/web/saga/SagaStateResponse.java
order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/KafkaCommandPublisher.java
order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/SagaCommand.java
order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/SagaCommandItem.java
order-service/src/main/java/zen/labs/order/adapter/outbound/persistence/SagaStateJpaDataStore.java
order-service/src/main/java/zen/labs/order/adapter/outbound/persistence/entity/SagaStateJpaEntity.java
order-service/src/main/java/zen/labs/order/adapter/outbound/persistence/repository/SagaStateRepository.java

order-service/src/test/java/zen/labs/order/domain/SagaStateTest.java
order-service/src/test/java/zen/labs/order/domain/SagaStatusTest.java
order-service/src/test/java/zen/labs/order/adapter/inbound/messaging/SagaReplyListenerTest.java
order-service/src/test/java/zen/labs/order/adapter/inbound/web/saga/SagaControllerTest.java
order-service/src/test/java/zen/labs/order/adapter/outbound/messaging/KafkaCommandPublisherTest.java
order-service/src/test/java/zen/labs/order/adapter/outbound/persistence/SagaStateJpaDataStoreTest.java
order-service/src/test/java/zen/labs/order/application/service/OrderSagaOrchestratorTest.java
order-service/src/test/java/zen/labs/order/application/service/SagaQueryServiceTest.java
```

- [ ] **Step 2.1: Delete all orchestration source files**

```bash
rm order-service/src/main/java/zen/labs/order/domain/SagaState.java \
   order-service/src/main/java/zen/labs/order/domain/SagaStatus.java \
   order-service/src/main/java/zen/labs/order/domain/SagaStep.java \
   order-service/src/main/java/zen/labs/order/domain/SagaDomainException.java \
   order-service/src/main/java/zen/labs/order/application/port/inbound/HandleSagaReplyUseCase.java \
   order-service/src/main/java/zen/labs/order/application/port/inbound/FindSagaUseCase.java \
   order-service/src/main/java/zen/labs/order/application/port/inbound/CancelOrderUseCase.java \
   order-service/src/main/java/zen/labs/order/application/port/outbound/SagaStateDataStore.java \
   order-service/src/main/java/zen/labs/order/application/port/outbound/SagaCommandPublisher.java \
   order-service/src/main/java/zen/labs/order/application/service/OrderSagaOrchestrator.java \
   order-service/src/main/java/zen/labs/order/application/service/SagaQueryService.java \
   order-service/src/main/java/zen/labs/order/adapter/inbound/messaging/SagaReplyListener.java \
   order-service/src/main/java/zen/labs/order/adapter/inbound/messaging/SagaReply.java \
   order-service/src/main/java/zen/labs/order/adapter/inbound/web/saga/SagaController.java \
   order-service/src/main/java/zen/labs/order/adapter/inbound/web/saga/SagaStateResponse.java \
   order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/KafkaCommandPublisher.java \
   order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/SagaCommand.java \
   order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/SagaCommandItem.java \
   order-service/src/main/java/zen/labs/order/adapter/outbound/persistence/SagaStateJpaDataStore.java \
   order-service/src/main/java/zen/labs/order/adapter/outbound/persistence/entity/SagaStateJpaEntity.java \
   order-service/src/main/java/zen/labs/order/adapter/outbound/persistence/repository/SagaStateRepository.java
```

- [ ] **Step 2.2: Delete orchestration test files**

```bash
rm order-service/src/test/java/zen/labs/order/domain/SagaStateTest.java \
   order-service/src/test/java/zen/labs/order/domain/SagaStatusTest.java \
   order-service/src/test/java/zen/labs/order/adapter/inbound/messaging/SagaReplyListenerTest.java \
   order-service/src/test/java/zen/labs/order/adapter/inbound/web/saga/SagaControllerTest.java \
   order-service/src/test/java/zen/labs/order/adapter/outbound/messaging/KafkaCommandPublisherTest.java \
   order-service/src/test/java/zen/labs/order/adapter/outbound/persistence/SagaStateJpaDataStoreTest.java \
   order-service/src/test/java/zen/labs/order/application/service/OrderSagaOrchestratorTest.java \
   order-service/src/test/java/zen/labs/order/application/service/SagaQueryServiceTest.java
```

---

## Task 3: Add choreography code to order-service

**New files:**
- Create: `order-service/src/main/java/zen/labs/order/application/port/inbound/CancelOrderUseCase.java`
- Create: `order-service/src/main/java/zen/labs/order/application/port/outbound/OrderEventPublisher.java`
- Create: `order-service/src/main/java/zen/labs/order/application/service/PlaceOrderService.java`
- Create: `order-service/src/main/java/zen/labs/order/application/service/CancelOrderService.java`
- Create: `order-service/src/main/java/zen/labs/order/application/service/SagaEventHandler.java`
- Create: `order-service/src/main/java/zen/labs/order/adapter/inbound/messaging/SagaEventListener.java`
- Create: `order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/KafkaOrderEventPublisher.java`
- Modify: `order-service/src/main/java/zen/labs/order/infrastructure/config/SagaTopicsProperties.java`
- Modify: `order-service/src/main/java/zen/labs/order/infrastructure/config/KafkaConsumerConfiguration.java`
- Modify: `order-service/src/main/java/zen/labs/order/adapter/inbound/web/order/OrderController.java`
- Modify: `order-service/src/main/resources/application.yml`
- Create: `order-service/src/main/resources/db/migration/V0006__drop-saga-state-table.sql`
- Modify: `order-service/src/test/java/zen/labs/order/OrderServiceArchitectureTest.java`

**Test files:**
- Create: `order-service/src/test/java/zen/labs/order/application/service/PlaceOrderServiceTest.java`
- Create: `order-service/src/test/java/zen/labs/order/application/service/CancelOrderServiceTest.java`
- Create: `order-service/src/test/java/zen/labs/order/application/service/SagaEventHandlerTest.java`
- Create: `order-service/src/test/java/zen/labs/order/adapter/outbound/messaging/KafkaOrderEventPublisherTest.java`

### Step 3.1: New inbound port — CancelOrderUseCase

- [ ] Create `order-service/src/main/java/zen/labs/order/application/port/inbound/CancelOrderUseCase.java`:

```java
package zen.labs.order.application.port.inbound;

public interface CancelOrderUseCase {
    void cancelOrder(String orderId);
}
```

### Step 3.2: New outbound port — OrderEventPublisher

- [ ] Create `order-service/src/main/java/zen/labs/order/application/port/outbound/OrderEventPublisher.java`:

```java
package zen.labs.order.application.port.outbound;

import java.math.BigDecimal;
import java.util.List;
import zen.labs.order.domain.OrderItem;

public interface OrderEventPublisher {
    void publishOrderPlaced(String orderId, List<OrderItem> items, BigDecimal amount, String shippingAddress);
    void publishCancellationRequested(String orderId);
}
```

### Step 3.3: Write PlaceOrderServiceTest first (TDD)

- [ ] Create `order-service/src/test/java/zen/labs/order/application/service/PlaceOrderServiceTest.java`:

```java
package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOrderService")
class PlaceOrderServiceTest {

    @Mock private OrderDataStore orderDataStore;
    @Mock private OrderEventPublisher eventPublisher;
    @Mock private ResourceIdSupplier resourceIdSupplier;
    @Mock private IdempotencyKeyStore idempotencyKeyStore;
    @InjectMocks private PlaceOrderService service;

    private static final String ORDER_ID = "order-001";
    private static final String CUSTOMER_ID = "cust-001";
    private static final String SHIPPING_ADDRESS = "123 Main St";

    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @Test
        @DisplayName("should save order with CONFIRMING status and publish ORDER_PLACED")
        void shouldSaveAndPublish() {
            var cmd = new PlaceOrderCommand(
                    null, CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item("prod-A", 2, BigDecimal.valueOf(10))),
                    SHIPPING_ADDRESS);
            when(resourceIdSupplier.nextResourceId()).thenReturn(ORDER_ID);
            var saved = Order.builder()
                    .resourceId(ORDER_ID).customerId(CUSTOMER_ID).status(OrderStatus.CONFIRMING)
                    .shippingAddress(SHIPPING_ADDRESS)
                    .items(List.of(new OrderItem("prod-A", 2, BigDecimal.valueOf(10))))
                    .build();
            when(orderDataStore.save(any())).thenReturn(saved);

            var result = service.placeOrder(cmd);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            verify(eventPublisher).publishOrderPlaced(
                    eq(ORDER_ID), any(), eq(BigDecimal.valueOf(20)), eq(SHIPPING_ADDRESS));
        }

        @Test
        @DisplayName("should return existing order and skip publish when idempotency key already exists")
        void shouldReturnExistingOrder_whenIdempotencyKeyPresent() {
            var cmd = new PlaceOrderCommand(
                    "idem-key-1", CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item("prod-A", 1, BigDecimal.TEN)),
                    SHIPPING_ADDRESS);
            var existing = Order.builder()
                    .resourceId(ORDER_ID).customerId(CUSTOMER_ID).status(OrderStatus.CONFIRMED)
                    .items(List.of()).shippingAddress(SHIPPING_ADDRESS).build();
            when(idempotencyKeyStore.findOrderId("idem-key-1")).thenReturn(Optional.of(ORDER_ID));
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.of(existing));

            var result = service.placeOrder(cmd);

            assertThat(result.getResourceId()).isEqualTo(ORDER_ID);
            verify(orderDataStore, never()).save(any());
            verify(eventPublisher, never()).publishOrderPlaced(any(), any(), any(), any());
        }
    }
}
```

- [ ] **Step 3.4: Run test to confirm it fails**

```bash
./gradlew :order-service:test --tests "zen.labs.order.application.service.PlaceOrderServiceTest"
```

Expected: compilation failure — `PlaceOrderService` does not exist yet.

### Step 3.5: Implement PlaceOrderService

- [ ] Create `order-service/src/main/java/zen/labs/order/application/service/PlaceOrderService.java`:

```java
package zen.labs.order.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.inbound.PlaceOrderUseCase;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderDataStore orderDataStore;
    private final OrderEventPublisher eventPublisher;
    private final ResourceIdSupplier resourceIdSupplier;
    private final IdempotencyKeyStore idempotencyKeyStore;

    @Override
    @Transactional
    public Order placeOrder(PlaceOrderCommand cmd) {
        if (cmd.idempotencyKey() != null) {
            var existingId = idempotencyKeyStore.findOrderId(cmd.idempotencyKey());
            if (existingId.isPresent()) {
                return orderDataStore.findByResourceId(existingId.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + existingId.get()));
            }
        }

        var order = Order.builder()
                .resourceId(resourceIdSupplier.nextResourceId())
                .customerId(cmd.customerId())
                .items(cmd.items().stream()
                        .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
                        .toList())
                .shippingAddress(cmd.shippingAddress())
                .status(OrderStatus.CONFIRMING)
                .createdAt(Instant.now())
                .build();
        var saved = orderDataStore.save(order);

        if (cmd.idempotencyKey() != null) {
            idempotencyKeyStore.save(cmd.idempotencyKey(), saved.getResourceId());
        }

        eventPublisher.publishOrderPlaced(
                saved.getResourceId(), saved.getItems(), saved.totalAmount(), saved.getShippingAddress());
        log.info("Order placed: orderId={}", saved.getResourceId());
        return saved;
    }
}
```

- [ ] **Step 3.6: Run PlaceOrderServiceTest — expect PASS**

```bash
./gradlew :order-service:test --tests "zen.labs.order.application.service.PlaceOrderServiceTest"
```

Expected: BUILD SUCCESSFUL (2 tests passing)

### Step 3.7: Write CancelOrderServiceTest first (TDD)

- [ ] Create `order-service/src/test/java/zen/labs/order/application/service/CancelOrderServiceTest.java`:

```java
package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOrderService")
class CancelOrderServiceTest {

    @Mock private OrderDataStore orderDataStore;
    @Mock private OrderEventPublisher eventPublisher;
    @InjectMocks private CancelOrderService service;

    private static final String ORDER_ID = "order-001";

    private Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .resourceId(ORDER_ID).customerId("cust-1")
                .status(status).shippingAddress("123 St").items(List.of()).build();
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should update order to CANCELLING and publish ORDER_CANCELLATION_REQUESTED when CONFIRMING")
        void shouldCancelWhenConfirming() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            service.cancelOrder(ORDER_ID);

            verify(orderDataStore).update(orderWithStatus(OrderStatus.CANCELLING));
            verify(eventPublisher).publishCancellationRequested(ORDER_ID);
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when order is CONFIRMED")
        void shouldReject_whenConfirmed() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMED)));

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID))
                    .isInstanceOf(UnprocessableRequestException.class);

            verify(eventPublisher, never()).publishCancellationRequested(any());
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when order is CANCELLED")
        void shouldReject_whenAlreadyCancelled() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLED)));

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID))
                    .isInstanceOf(UnprocessableRequestException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order not found")
        void shouldThrow_whenOrderNotFound() {
            when(orderDataStore.findByResourceId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelOrder(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
```

- [ ] **Step 3.8: Run test to confirm it fails (CancelOrderService not yet created)**

```bash
./gradlew :order-service:test --tests "zen.labs.order.application.service.CancelOrderServiceTest"
```

Expected: compilation failure.

### Step 3.9: Implement CancelOrderService

- [ ] Create `order-service/src/main/java/zen/labs/order/application/service/CancelOrderService.java`:

```java
package zen.labs.order.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.order.application.port.inbound.CancelOrderUseCase;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.OrderStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderDataStore orderDataStore;
    private final OrderEventPublisher eventPublisher;

    @Override
    @Transactional
    public void cancelOrder(String orderId) {
        var order = orderDataStore.findByResourceId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getStatus() != OrderStatus.CONFIRMING) {
            throw new UnprocessableRequestException(
                    "Order cannot be cancelled in status: " + order.getStatus());
        }
        orderDataStore.update(order.withStatus(OrderStatus.CANCELLING));
        eventPublisher.publishCancellationRequested(orderId);
        log.info("Order cancellation requested: orderId={}", orderId);
    }
}
```

- [ ] **Step 3.10: Run CancelOrderServiceTest — expect PASS**

```bash
./gradlew :order-service:test --tests "zen.labs.order.application.service.CancelOrderServiceTest"
```

Expected: BUILD SUCCESSFUL (4 tests passing)

### Step 3.11: Write SagaEventHandlerTest first (TDD)

- [ ] Create `order-service/src/test/java/zen/labs/order/application/service/SagaEventHandlerTest.java`:

```java
package zen.labs.order.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaEventHandler")
class SagaEventHandlerTest {

    @Mock private OrderDataStore orderDataStore;
    @InjectMocks private SagaEventHandler handler;

    private static final String ORDER_ID = "order-001";

    private Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .resourceId(ORDER_ID).customerId("cust-1")
                .status(status).shippingAddress("123 St").items(List.of()).build();
    }

    @Nested
    @DisplayName("onInventoryReservationFailed")
    class OnInventoryReservationFailed {

        @Test
        @DisplayName("should mark order CANCELLED")
        void shouldMarkCancelled() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            handler.onInventoryReservationFailed(ORDER_ID);

            verify(orderDataStore).update(orderWithStatus(OrderStatus.CANCELLED));
        }
    }

    @Nested
    @DisplayName("onInventoryReleased")
    class OnInventoryReleased {

        @Test
        @DisplayName("should mark order CANCELLED when CONFIRMING")
        void shouldMarkCancelled_whenConfirming() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            handler.onInventoryReleased(ORDER_ID);

            verify(orderDataStore).update(orderWithStatus(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("should mark order CANCELLED when CANCELLING")
        void shouldMarkCancelled_whenCancelling() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLING)));

            handler.onInventoryReleased(ORDER_ID);

            verify(orderDataStore).update(orderWithStatus(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("should NOT update order when already CONFIRMED")
        void shouldSkip_whenAlreadyConfirmed() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMED)));

            handler.onInventoryReleased(ORDER_ID);

            verify(orderDataStore, never()).update(any());
        }
    }

    @Nested
    @DisplayName("onShipmentCreated")
    class OnShipmentCreated {

        @Test
        @DisplayName("should mark order CONFIRMED when CONFIRMING")
        void shouldMarkConfirmed_whenConfirming() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CONFIRMING)));

            handler.onShipmentCreated(ORDER_ID);

            verify(orderDataStore).update(orderWithStatus(OrderStatus.CONFIRMED));
        }

        @Test
        @DisplayName("should NOT update order when CANCELLING (race condition guard)")
        void shouldSkip_whenCancelling() {
            when(orderDataStore.findByResourceId(ORDER_ID))
                    .thenReturn(Optional.of(orderWithStatus(OrderStatus.CANCELLING)));

            handler.onShipmentCreated(ORDER_ID);

            verify(orderDataStore, never()).update(any());
        }
    }
}
```

- [ ] **Step 3.12: Run test to confirm it fails**

```bash
./gradlew :order-service:test --tests "zen.labs.order.application.service.SagaEventHandlerTest"
```

Expected: compilation failure — `SagaEventHandler` does not exist yet.

### Step 3.13: Implement SagaEventHandler

- [ ] Create `order-service/src/main/java/zen/labs/order/application/service/SagaEventHandler.java`:

```java
package zen.labs.order.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaEventHandler {

    private final OrderDataStore orderDataStore;

    @Transactional
    public void onInventoryReservationFailed(String orderId) {
        updateStatus(orderId, OrderStatus.CANCELLED);
        log.info("Order CANCELLED (inventory failed): orderId={}", orderId);
    }

    @Transactional
    public void onInventoryReleased(String orderId) {
        var order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            orderDataStore.update(order.withStatus(OrderStatus.CANCELLED));
            log.info("Order CANCELLED (inventory released): orderId={}", orderId);
        }
    }

    @Transactional
    public void onShipmentCreated(String orderId) {
        var order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMING) {
            orderDataStore.update(order.withStatus(OrderStatus.CONFIRMED));
            log.info("Order CONFIRMED: orderId={}", orderId);
        }
    }

    private void updateStatus(String orderId, OrderStatus status) {
        orderDataStore.update(requireOrder(orderId).withStatus(status));
    }

    private Order requireOrder(String orderId) {
        return orderDataStore.findByResourceId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }
}
```

- [ ] **Step 3.14: Run SagaEventHandlerTest — expect PASS**

```bash
./gradlew :order-service:test --tests "zen.labs.order.application.service.SagaEventHandlerTest"
```

Expected: BUILD SUCCESSFUL (5 tests passing)

### Step 3.15: Implement KafkaOrderEventPublisher

- [ ] Create `order-service/src/main/java/zen/labs/order/adapter/outbound/messaging/KafkaOrderEventPublisher.java`:

```java
package zen.labs.order.adapter.outbound.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventItem;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.order.application.port.outbound.OrderEventPublisher;
import zen.labs.order.domain.OrderItem;

@Component
@Slf4j
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String orderEventsTopic;

    public KafkaOrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.order-events}") String orderEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.orderEventsTopic = orderEventsTopic;
    }

    @Override
    public void publishOrderPlaced(String orderId, List<OrderItem> items, BigDecimal amount, String shippingAddress) {
        var event = new SagaEvent(
                UUID.randomUUID().toString(),
                SagaEventType.ORDER_PLACED,
                orderId, null,
                items.stream().map(i -> new SagaEventItem(i.productId(), i.quantity())).toList(),
                amount,
                shippingAddress);
        publish(orderId, event);
    }

    @Override
    public void publishCancellationRequested(String orderId) {
        var event = new SagaEvent(
                UUID.randomUUID().toString(),
                SagaEventType.ORDER_CANCELLATION_REQUESTED,
                orderId, null, null, null, null);
        publish(orderId, event);
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate.send(orderEventsTopic, key, jsonMapper.writeValueAsString(event));
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish event: " + event.type(), e);
        }
    }
}
```

### Step 3.16: Implement SagaEventListener

- [ ] Create `order-service/src/main/java/zen/labs/order/adapter/inbound/messaging/SagaEventListener.java`:

```java
package zen.labs.order.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.order.application.service.SagaEventHandler;

@Component
@Slf4j
public class SagaEventListener {

    private final SagaEventHandler eventHandler;
    private final JsonMapper jsonMapper;

    public SagaEventListener(SagaEventHandler eventHandler, JsonMapper jsonMapper) {
        this.eventHandler = eventHandler;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = {
            "${application.saga.topics.inventory-events}",
            "${application.saga.topics.payment-events}",
            "${application.saga.topics.shipping-events}"
    })
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            MDC.put("eventType", event.type().name());
            log.debug("Received saga event: type={} orderId={}", event.type(), event.orderId());
            dispatch(event);
        } catch (Exception e) {
            log.error("Failed to process saga event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }

    private void dispatch(SagaEvent event) {
        switch (event.type()) {
            case INVENTORY_RESERVATION_FAILED -> eventHandler.onInventoryReservationFailed(event.orderId());
            case INVENTORY_RELEASED           -> eventHandler.onInventoryReleased(event.orderId());
            case SHIPMENT_CREATED             -> eventHandler.onShipmentCreated(event.orderId());
            default -> log.debug("Ignoring event type: {}", event.type());
        }
    }
}
```

### Step 3.17: Update SagaTopicsProperties

- [ ] Replace `order-service/src/main/java/zen/labs/order/infrastructure/config/SagaTopicsProperties.java`:

```java
package zen.labs.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String orderEvents,
        String inventoryEvents,
        String paymentEvents,
        String shippingEvents) {}
```

### Step 3.18: Update KafkaConsumerConfiguration

- [ ] Replace `order-service/src/main/java/zen/labs/order/infrastructure/config/KafkaConsumerConfiguration.java`:

```java
package zen.labs.order.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@EnableConfigurationProperties(SagaTopicsProperties.class)
public class KafkaConsumerConfiguration {}
```

### Step 3.19: Update application.yml — replace saga topics

- [ ] In `order-service/src/main/resources/application.yml`, replace the `application.saga.topics` block:

```yaml
application:
  default-page-limit: 25
  saga:
    topics:
      order-events: kafka-zen.lab-802.saga.order.events
      inventory-events: kafka-zen.lab-802.saga.inventory.events
      payment-events: kafka-zen.lab-802.saga.payment.events
      shipping-events: kafka-zen.lab-802.saga.shipping.events
```

- [ ] Apply the same change to `order-service/src/integrationTest/resources/application-test.yml` — add/replace the same four topic keys.

- [ ] Apply the same change to `order-service/src/test/resources/application-test.yml`.

### Step 3.20: Update OrderController — remove saga endpoints

- [ ] Open `order-service/src/main/java/zen/labs/order/adapter/inbound/web/order/OrderController.java`.

  Find any injected `FindSagaUseCase`/`SagaQueryService` fields and remove them. The cancel endpoint should now inject `CancelOrderUseCase` (backed by `CancelOrderService`). All saga-related endpoint methods (`GET /sagas`, `GET /sagas/{id}`) must be removed.

  The controller should retain: POST /orders, GET /orders, GET /orders/{id}, POST /orders/{id}/cancel.

### Step 3.21: Add Flyway migration to drop saga_state table

- [ ] Create `order-service/src/main/resources/db/migration/V0006__drop-saga-state-table.sql`:

```sql
drop table if exists order_service.saga_state;
```

### Step 3.22: Update OrderServiceArchitectureTest

- [ ] Open `order-service/src/test/java/zen/labs/order/OrderServiceArchitectureTest.java`.

  Remove any `@ArchTest` rules that reference deleted packages or classes:
  - Any rule mentioning `saga.state`, `SagaController`, `SagaState`, or the old `SagaCommandPublisher`
  - Keep all remaining layer and naming-convention rules

### Step 3.23: Build order-service — expect GREEN

```bash
./gradlew :order-service:build
```

Expected: BUILD SUCCESSFUL. All unit tests pass. Integration tests may be skipped if Docker is unavailable — that's acceptable at this step.

- [ ] **Step 3.24: Commit**

```bash
git add order-service/ common-adapter-components/
git commit -m "refactor(order-service): replace orchestrator with choreographed event model"
```

---

## Task 4: Refactor inventory-service

**Files to delete:**
```
inventory-service/src/main/java/zen/labs/inventory/application/port/inbound/HandleInventoryCommandUseCase.java
inventory-service/src/main/java/zen/labs/inventory/application/port/outbound/SagaReplyPublisher.java
inventory-service/src/main/java/zen/labs/inventory/application/service/InventorySagaParticipant.java
inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommandListener.java
inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommand.java
inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommandItem.java
inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/messaging/KafkaReplyPublisher.java
inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/messaging/SagaReply.java

inventory-service/src/test/java/zen/labs/inventory/application/service/InventorySagaParticipantTest.java
inventory-service/src/test/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommandListenerTest.java
inventory-service/src/test/java/zen/labs/inventory/adapter/outbound/messaging/KafkaReplyPublisherTest.java
```

**New files:**
- Modify: `inventory-service/src/main/java/zen/labs/inventory/domain/InventoryReservation.java` (remove sagaId)
- Modify: `inventory-service/src/main/java/zen/labs/inventory/application/port/outbound/InventoryReservationDataStore.java` (rename findBySagaId → findByOrderId)
- Modify: `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/persistence/repository/InventoryReservationRepository.java`
- Modify: `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/persistence/InventoryReservationJpaDataStore.java`
- Modify: `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/persistence/entity/InventoryReservationJpaEntity.java`
- Create: `inventory-service/src/main/java/zen/labs/inventory/application/port/outbound/InventoryEventPublisher.java`
- Create: `inventory-service/src/main/java/zen/labs/inventory/application/service/InventoryEventService.java`
- Create: `inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/OrderEventListener.java`
- Create: `inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/PaymentEventListener.java`
- Create: `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/messaging/KafkaInventoryEventPublisher.java`
- Modify: `inventory-service/src/main/java/zen/labs/inventory/infrastructure/config/SagaTopicsProperties.java`
- Modify: `inventory-service/src/main/java/zen/labs/inventory/infrastructure/config/KafkaConsumerConfiguration.java`
- Modify: `inventory-service/src/main/resources/application.yml`
- Create: `inventory-service/src/main/resources/db/migration/V0003__drop-saga-id-from-reservation.sql`

- [ ] **Step 4.1: Delete orchestration files**

```bash
rm inventory-service/src/main/java/zen/labs/inventory/application/port/inbound/HandleInventoryCommandUseCase.java \
   inventory-service/src/main/java/zen/labs/inventory/application/port/outbound/SagaReplyPublisher.java \
   inventory-service/src/main/java/zen/labs/inventory/application/service/InventorySagaParticipant.java \
   inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommandListener.java \
   inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommand.java \
   inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommandItem.java \
   inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/messaging/KafkaReplyPublisher.java \
   inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/messaging/SagaReply.java

rm inventory-service/src/test/java/zen/labs/inventory/application/service/InventorySagaParticipantTest.java \
   inventory-service/src/test/java/zen/labs/inventory/adapter/inbound/messaging/SagaCommandListenerTest.java \
   inventory-service/src/test/java/zen/labs/inventory/adapter/outbound/messaging/KafkaReplyPublisherTest.java
```

- [ ] **Step 4.2: Remove sagaId from InventoryReservation domain**

Open `inventory-service/src/main/java/zen/labs/inventory/domain/InventoryReservation.java`.  
Remove the `sagaId` field from the `@Builder` class. The domain object should only have: `orderId`, `status`, `reservedAt`.

- [ ] **Step 4.3: Remove sagaId from InventoryReservationJpaEntity**

Open `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/persistence/entity/InventoryReservationJpaEntity.java`.  
Remove the `sagaId` column field. Ensure `orderId` has a `@Column(unique = true)` or an index (one reservation per order).

- [ ] **Step 4.4: Update InventoryReservationRepository**

Open `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/persistence/repository/InventoryReservationRepository.java`.  
Rename `findBySagaId(String sagaId)` → `findByOrderId(String orderId)`.

- [ ] **Step 4.5: Update InventoryReservationDataStore port**

Open `inventory-service/src/main/java/zen/labs/inventory/application/port/outbound/InventoryReservationDataStore.java`.  
Rename `findBySagaId(String sagaId)` → `findByOrderId(String orderId)`.

- [ ] **Step 4.6: Update InventoryReservationJpaDataStore adapter**

Open `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/persistence/InventoryReservationJpaDataStore.java`.  
Update the `findBySagaId` call → `findByOrderId`. Remove any `sagaId` field mapping in `toDomain` and `toEntity` methods.

- [ ] **Step 4.7: Add Flyway migration to remove saga_id column**

Create `inventory-service/src/main/resources/db/migration/V0003__drop-saga-id-from-reservation.sql`:

```sql
alter table inventory_service.inventory_reservation drop column if exists saga_id;
```

- [ ] **Step 4.8: Create InventoryEventPublisher port**

Create `inventory-service/src/main/java/zen/labs/inventory/application/port/outbound/InventoryEventPublisher.java`:

```java
package zen.labs.inventory.application.port.outbound;

public interface InventoryEventPublisher {
    void publishInventoryReserved(String orderId);
    void publishInventoryReservationFailed(String orderId, String reason);
    void publishInventoryReleased(String orderId);
}
```

- [ ] **Step 4.9: Write InventoryEventServiceTest first (TDD)**

Create `inventory-service/src/test/java/zen/labs/inventory/application/service/InventoryEventServiceTest.java`:

```java
package zen.labs.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.adapters.common.kafka.SagaEventItem;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.domain.InventoryReservation;
import zen.labs.inventory.domain.ReservationStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryEventService")
class InventoryEventServiceTest {

    @Mock private InventoryReservationDataStore reservationDataStore;
    @Mock private InventoryEventPublisher eventPublisher;
    @InjectMocks private InventoryEventService service;

    private static final String ORDER_ID = "order-001";

    @Nested
    @DisplayName("handleOrderPlaced")
    class HandleOrderPlaced {

        @Test
        @DisplayName("should save reservation and publish INVENTORY_RESERVED when quantity within limit")
        void shouldReserveAndPublish_whenWithinLimit() {
            var items = List.of(new SagaEventItem("prod-A", 10), new SagaEventItem("prod-B", 100));
            when(reservationDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleOrderPlaced(ORDER_ID, items);

            ArgumentCaptor<InventoryReservation> captor = ArgumentCaptor.forClass(InventoryReservation.class);
            verify(reservationDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
            verify(eventPublisher).publishInventoryReserved(ORDER_ID);
        }

        @Test
        @DisplayName("should NOT save and publish INVENTORY_RESERVATION_FAILED when any item exceeds 100")
        void shouldFailAndPublish_whenItemExceedsLimit() {
            var items = List.of(new SagaEventItem("prod-X", 101));

            service.handleOrderPlaced(ORDER_ID, items);

            verify(reservationDataStore, never()).save(any());
            verify(eventPublisher).publishInventoryReservationFailed(
                    ORDER_ID, "Insufficient stock for product: prod-X");
        }
    }

    @Nested
    @DisplayName("handleRelease")
    class HandleRelease {

        @Test
        @DisplayName("should update status to RELEASED and publish INVENTORY_RELEASED when reservation found")
        void shouldReleaseAndPublish_whenReservationFound() {
            var existing = InventoryReservation.builder()
                    .orderId(ORDER_ID).status(ReservationStatus.RESERVED).reservedAt(Instant.now()).build();
            when(reservationDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleRelease(ORDER_ID);

            verify(reservationDataStore).update(existing.withStatus(ReservationStatus.RELEASED));
            verify(eventPublisher).publishInventoryReleased(ORDER_ID);
        }

        @Test
        @DisplayName("should still publish INVENTORY_RELEASED when no reservation found")
        void shouldStillPublish_whenNoReservation() {
            when(reservationDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleRelease(ORDER_ID);

            verify(reservationDataStore, never()).update(any());
            verify(eventPublisher).publishInventoryReleased(ORDER_ID);
        }
    }
}
```

- [ ] **Step 4.10: Run test to confirm it fails**

```bash
./gradlew :inventory-service:test --tests "zen.labs.inventory.application.service.InventoryEventServiceTest"
```

Expected: compilation failure.

- [ ] **Step 4.11: Implement InventoryEventService**

Create `inventory-service/src/main/java/zen/labs/inventory/application/service/InventoryEventService.java`:

```java
package zen.labs.inventory.application.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.adapters.common.kafka.SagaEventItem;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;
import zen.labs.inventory.application.port.outbound.InventoryReservationDataStore;
import zen.labs.inventory.domain.InventoryReservation;
import zen.labs.inventory.domain.ReservationStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventService {

    private final InventoryReservationDataStore reservationDataStore;
    private final InventoryEventPublisher eventPublisher;

    @Transactional
    public void handleOrderPlaced(String orderId, List<SagaEventItem> items) {
        var exceeded = items.stream().filter(i -> i.quantity() > 100).findFirst();
        if (exceeded.isPresent()) {
            log.warn("Inventory reservation failed: orderId={} productId={}", orderId, exceeded.get().productId());
            eventPublisher.publishInventoryReservationFailed(
                    orderId, "Insufficient stock for product: " + exceeded.get().productId());
            return;
        }
        var reservation = InventoryReservation.builder()
                .orderId(orderId)
                .status(ReservationStatus.RESERVED)
                .reservedAt(Instant.now())
                .build();
        reservationDataStore.save(reservation);
        log.info("Inventory reserved: orderId={}", orderId);
        eventPublisher.publishInventoryReserved(orderId);
    }

    @Transactional
    public void handleRelease(String orderId) {
        reservationDataStore.findByOrderId(orderId).ifPresentOrElse(
                r -> {
                    reservationDataStore.update(r.withStatus(ReservationStatus.RELEASED));
                    log.info("Inventory released: orderId={}", orderId);
                },
                () -> log.warn("No reservation to release: orderId={}", orderId));
        eventPublisher.publishInventoryReleased(orderId);
    }
}
```

- [ ] **Step 4.12: Run InventoryEventServiceTest — expect PASS**

```bash
./gradlew :inventory-service:test --tests "zen.labs.inventory.application.service.InventoryEventServiceTest"
```

Expected: BUILD SUCCESSFUL (4 tests passing)

- [ ] **Step 4.13: Implement KafkaInventoryEventPublisher**

Create `inventory-service/src/main/java/zen/labs/inventory/adapter/outbound/messaging/KafkaInventoryEventPublisher.java`:

```java
package zen.labs.inventory.adapter.outbound.messaging;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.inventory.application.port.outbound.InventoryEventPublisher;

@Component
@Slf4j
public class KafkaInventoryEventPublisher implements InventoryEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String inventoryEventsTopic;

    public KafkaInventoryEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.inventory-events}") String inventoryEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.inventoryEventsTopic = inventoryEventsTopic;
    }

    @Override
    public void publishInventoryReserved(String orderId) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.INVENTORY_RESERVED,
                orderId, null, null, null, null));
    }

    @Override
    public void publishInventoryReservationFailed(String orderId, String reason) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.INVENTORY_RESERVATION_FAILED,
                orderId, reason, null, null, null));
    }

    @Override
    public void publishInventoryReleased(String orderId) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.INVENTORY_RELEASED,
                orderId, null, null, null, null));
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate.send(inventoryEventsTopic, key, jsonMapper.writeValueAsString(event));
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish inventory event: " + event.type(), e);
        }
    }
}
```

- [ ] **Step 4.14: Create OrderEventListener (subscribes to order-events)**

Create `inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/OrderEventListener.java`:

```java
package zen.labs.inventory.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.inventory.application.service.InventoryEventService;

@Component
@Slf4j
public class OrderEventListener {

    private final InventoryEventService eventService;
    private final JsonMapper jsonMapper;

    public OrderEventListener(InventoryEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}-order")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            switch (event.type()) {
                case ORDER_PLACED -> eventService.handleOrderPlaced(event.orderId(), event.items());
                case ORDER_CANCELLATION_REQUESTED -> eventService.handleRelease(event.orderId());
                default -> log.debug("Ignoring order event: {}", event.type());
            }
        } catch (Exception e) {
            log.error("Failed to process order event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

- [ ] **Step 4.15: Create PaymentEventListener (subscribes to payment-events)**

Create `inventory-service/src/main/java/zen/labs/inventory/adapter/inbound/messaging/PaymentEventListener.java`:

```java
package zen.labs.inventory.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.inventory.application.service.InventoryEventService;

@Component
@Slf4j
public class PaymentEventListener {

    private final InventoryEventService eventService;
    private final JsonMapper jsonMapper;

    public PaymentEventListener(InventoryEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}-payment")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            switch (event.type()) {
                case PAYMENT_FAILED, PAYMENT_REFUNDED -> eventService.handleRelease(event.orderId());
                default -> log.debug("Ignoring payment event: {}", event.type());
            }
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

- [ ] **Step 4.16: Update SagaTopicsProperties**

Replace `inventory-service/src/main/java/zen/labs/inventory/infrastructure/config/SagaTopicsProperties.java`:

```java
package zen.labs.inventory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String inventoryEvents,
        String orderEvents,
        String paymentEvents) {}
```

- [ ] **Step 4.17: Update KafkaConsumerConfiguration**

Replace `inventory-service/src/main/java/zen/labs/inventory/infrastructure/config/KafkaConsumerConfiguration.java`:

```java
package zen.labs.inventory.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@EnableConfigurationProperties(SagaTopicsProperties.class)
public class KafkaConsumerConfiguration {}
```

- [ ] **Step 4.18: Update application.yml**

In `inventory-service/src/main/resources/application.yml`, replace `application.saga.topics`:

```yaml
application:
  default-page-limit: 25
  saga:
    topics:
      inventory-events: kafka-zen.lab-802.saga.inventory.events
      order-events: kafka-zen.lab-802.saga.order.events
      payment-events: kafka-zen.lab-802.saga.payment.events
```

Apply the same replacement to `inventory-service/src/integrationTest/resources/application-test.yml` and `inventory-service/src/test/resources/application-test.yml`.

- [ ] **Step 4.19: Build inventory-service — expect GREEN**

```bash
./gradlew :inventory-service:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4.20: Commit**

```bash
git add inventory-service/
git commit -m "refactor(inventory-service): replace command/reply with event-driven choreography"
```

---

## Task 5: Refactor payment-service

**Files to delete:**
```
payment-service/src/main/java/zen/labs/payment/application/port/inbound/HandlePaymentCommandUseCase.java
payment-service/src/main/java/zen/labs/payment/application/port/outbound/SagaReplyPublisher.java
payment-service/src/main/java/zen/labs/payment/application/service/PaymentSagaParticipant.java
payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/SagaCommandListener.java
payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/SagaCommand.java
payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/SagaCommandItem.java
payment-service/src/main/java/zen/labs/payment/adapter/outbound/messaging/KafkaReplyPublisher.java
payment-service/src/main/java/zen/labs/payment/adapter/outbound/messaging/SagaReply.java

payment-service/src/test/java/zen/labs/payment/application/service/PaymentSagaParticipantTest.java
payment-service/src/test/java/zen/labs/payment/adapter/inbound/messaging/SagaCommandListenerTest.java
payment-service/src/test/java/zen/labs/payment/adapter/outbound/messaging/KafkaReplyPublisherTest.java
```

- [ ] **Step 5.1: Delete orchestration files**

```bash
rm payment-service/src/main/java/zen/labs/payment/application/port/inbound/HandlePaymentCommandUseCase.java \
   payment-service/src/main/java/zen/labs/payment/application/port/outbound/SagaReplyPublisher.java \
   payment-service/src/main/java/zen/labs/payment/application/service/PaymentSagaParticipant.java \
   payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/SagaCommandListener.java \
   payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/SagaCommand.java \
   payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/SagaCommandItem.java \
   payment-service/src/main/java/zen/labs/payment/adapter/outbound/messaging/KafkaReplyPublisher.java \
   payment-service/src/main/java/zen/labs/payment/adapter/outbound/messaging/SagaReply.java

rm payment-service/src/test/java/zen/labs/payment/application/service/PaymentSagaParticipantTest.java \
   payment-service/src/test/java/zen/labs/payment/adapter/inbound/messaging/SagaCommandListenerTest.java \
   payment-service/src/test/java/zen/labs/payment/adapter/outbound/messaging/KafkaReplyPublisherTest.java
```

- [ ] **Step 5.2: Remove sagaId from Payment domain + persistence**

  - `payment-service/.../domain/Payment.java`: remove `sagaId` field
  - `payment-service/.../persistence/entity/PaymentJpaEntity.java`: remove `sagaId` column field
  - `payment-service/.../persistence/repository/PaymentRepository.java`: rename `findBySagaId` → `findByOrderId`
  - `payment-service/.../persistence/PaymentJpaDataStore.java`: update repository call + toDomain/toEntity mappings

- [ ] **Step 5.3: Add Flyway migration**

Create `payment-service/src/main/resources/db/migration/V0003__drop-saga-id-from-payment.sql`:

```sql
alter table payment_service.payment drop column if exists saga_id;
```

- [ ] **Step 5.4: Create PaymentEventPublisher port**

Create `payment-service/src/main/java/zen/labs/payment/application/port/outbound/PaymentEventPublisher.java`:

```java
package zen.labs.payment.application.port.outbound;

public interface PaymentEventPublisher {
    void publishPaymentProcessed(String orderId);
    void publishPaymentFailed(String orderId, String reason);
    void publishPaymentRefunded(String orderId);
}
```

- [ ] **Step 5.5: Write PaymentEventServiceTest first (TDD)**

Create `payment-service/src/test/java/zen/labs/payment/application/service/PaymentEventServiceTest.java`:

```java
package zen.labs.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.payment.application.port.outbound.PaymentDataStore;
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventService")
class PaymentEventServiceTest {

    @Mock private PaymentDataStore paymentDataStore;
    @Mock private PaymentEventPublisher eventPublisher;
    @InjectMocks private PaymentEventService service;

    private static final String ORDER_ID = "order-001";

    @Nested
    @DisplayName("handleInventoryReserved")
    class HandleInventoryReserved {

        @Test
        @DisplayName("should save payment and publish PAYMENT_PROCESSED when amount within limit")
        void shouldProcessAndPublish_whenWithinLimit() {
            when(paymentDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleInventoryReserved(ORDER_ID, BigDecimal.valueOf(5_000));

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            verify(eventPublisher).publishPaymentProcessed(ORDER_ID);
        }

        @Test
        @DisplayName("should NOT save and publish PAYMENT_FAILED when amount exceeds 10000")
        void shouldFailAndPublish_whenAmountExceedsLimit() {
            service.handleInventoryReserved(ORDER_ID, BigDecimal.valueOf(10_001));

            verify(paymentDataStore, never()).save(any());
            verify(eventPublisher).publishPaymentFailed(any(), any());
        }
    }

    @Nested
    @DisplayName("handleShipmentFailed (refund)")
    class HandleShipmentFailed {

        @Test
        @DisplayName("should refund payment and publish PAYMENT_REFUNDED when payment found")
        void shouldRefundAndPublish_whenPaymentFound() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID).amount(BigDecimal.TEN)
                    .status(PaymentStatus.PROCESSED).processedAt(Instant.now()).build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleShipmentFailed(ORDER_ID);

            verify(paymentDataStore).update(existing.withStatus(PaymentStatus.REFUNDED));
            verify(eventPublisher).publishPaymentRefunded(ORDER_ID);
        }

        @Test
        @DisplayName("should still publish PAYMENT_REFUNDED when no payment found")
        void shouldStillPublish_whenNoPayment() {
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleShipmentFailed(ORDER_ID);

            verify(paymentDataStore, never()).update(any());
            verify(eventPublisher).publishPaymentRefunded(ORDER_ID);
        }
    }

    @Nested
    @DisplayName("handleOrderCancellationRequested")
    class HandleOrderCancellationRequested {

        @Test
        @DisplayName("should refund payment when payment exists (no event published)")
        void shouldRefund_whenPaymentFound() {
            var existing = Payment.builder()
                    .orderId(ORDER_ID).amount(BigDecimal.TEN)
                    .status(PaymentStatus.PROCESSED).processedAt(Instant.now()).build();
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(paymentDataStore).update(existing.withStatus(PaymentStatus.REFUNDED));
            verify(eventPublisher, never()).publishPaymentRefunded(any());
        }

        @Test
        @DisplayName("should do nothing when no payment exists")
        void shouldDoNothing_whenNoPayment() {
            when(paymentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(paymentDataStore, never()).update(any());
        }
    }
}
```

- [ ] **Step 5.6: Run test to confirm it fails**

```bash
./gradlew :payment-service:test --tests "zen.labs.payment.application.service.PaymentEventServiceTest"
```

Expected: compilation failure.

- [ ] **Step 5.7: Implement PaymentEventService**

Create `payment-service/src/main/java/zen/labs/payment/application/service/PaymentEventService.java`:

```java
package zen.labs.payment.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.payment.application.port.outbound.PaymentDataStore;
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;
import zen.labs.payment.domain.Payment;
import zen.labs.payment.domain.PaymentStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventService {

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(10_000);

    private final PaymentDataStore paymentDataStore;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public void handleInventoryReserved(String orderId, BigDecimal amount) {
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            log.warn("Payment failed: orderId={} amount={}", orderId, amount);
            eventPublisher.publishPaymentFailed(orderId, "Amount exceeds maximum allowed: " + amount);
            return;
        }
        var payment = Payment.builder()
                .orderId(orderId).amount(amount)
                .status(PaymentStatus.PROCESSED).processedAt(Instant.now())
                .build();
        paymentDataStore.save(payment);
        log.info("Payment processed: orderId={}", orderId);
        eventPublisher.publishPaymentProcessed(orderId);
    }

    @Transactional
    public void handleShipmentFailed(String orderId) {
        paymentDataStore.findByOrderId(orderId).ifPresentOrElse(
                p -> {
                    paymentDataStore.update(p.withStatus(PaymentStatus.REFUNDED));
                    log.info("Payment refunded (shipment failed): orderId={}", orderId);
                },
                () -> log.warn("No payment to refund: orderId={}", orderId));
        eventPublisher.publishPaymentRefunded(orderId);
    }

    @Transactional
    public void handleOrderCancellationRequested(String orderId) {
        paymentDataStore.findByOrderId(orderId).ifPresentOrElse(
                p -> {
                    paymentDataStore.update(p.withStatus(PaymentStatus.REFUNDED));
                    log.info("Payment refunded (order cancelled): orderId={}", orderId);
                },
                () -> log.debug("No payment to refund for cancelled order: orderId={}", orderId));
    }
}
```

- [ ] **Step 5.8: Run PaymentEventServiceTest — expect PASS**

```bash
./gradlew :payment-service:test --tests "zen.labs.payment.application.service.PaymentEventServiceTest"
```

Expected: BUILD SUCCESSFUL (5 tests passing)

- [ ] **Step 5.9: Implement KafkaPaymentEventPublisher**

Create `payment-service/src/main/java/zen/labs/payment/adapter/outbound/messaging/KafkaPaymentEventPublisher.java`:

```java
package zen.labs.payment.adapter.outbound.messaging;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.payment.application.port.outbound.PaymentEventPublisher;

@Component
@Slf4j
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String paymentEventsTopic;

    public KafkaPaymentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.payment-events}") String paymentEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    @Override
    public void publishPaymentProcessed(String orderId) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.PAYMENT_PROCESSED,
                orderId, null, null, null, null));
    }

    @Override
    public void publishPaymentFailed(String orderId, String reason) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.PAYMENT_FAILED,
                orderId, reason, null, null, null));
    }

    @Override
    public void publishPaymentRefunded(String orderId) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.PAYMENT_REFUNDED,
                orderId, null, null, null, null));
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate.send(paymentEventsTopic, key, jsonMapper.writeValueAsString(event));
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish payment event: " + event.type(), e);
        }
    }
}
```

- [ ] **Step 5.10: Create InventoryEventListener and ShippingEventListener for payment-service**

Create `payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/InventoryEventListener.java`:

```java
package zen.labs.payment.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.payment.application.service.PaymentEventService;

@Component
@Slf4j
public class InventoryEventListener {

    private final PaymentEventService eventService;
    private final JsonMapper jsonMapper;

    public InventoryEventListener(PaymentEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.inventory-events}", groupId = "${spring.kafka.consumer.group-id}-inventory")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == zen.labs.adapters.common.kafka.SagaEventType.INVENTORY_RESERVED) {
                eventService.handleInventoryReserved(event.orderId(), event.amount());
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

Create `payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/ShippingEventListener.java`:

```java
package zen.labs.payment.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.payment.application.service.PaymentEventService;

@Component
@Slf4j
public class ShippingEventListener {

    private final PaymentEventService eventService;
    private final JsonMapper jsonMapper;

    public ShippingEventListener(PaymentEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.shipping-events}", groupId = "${spring.kafka.consumer.group-id}-shipping")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == zen.labs.adapters.common.kafka.SagaEventType.SHIPMENT_FAILED) {
                eventService.handleShipmentFailed(event.orderId());
            }
        } catch (Exception e) {
            log.error("Failed to process shipping event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

Create `payment-service/src/main/java/zen/labs/payment/adapter/inbound/messaging/OrderEventListener.java`:

```java
package zen.labs.payment.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.payment.application.service.PaymentEventService;

@Component
@Slf4j
public class OrderEventListener {

    private final PaymentEventService eventService;
    private final JsonMapper jsonMapper;

    public OrderEventListener(PaymentEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}-order")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == zen.labs.adapters.common.kafka.SagaEventType.ORDER_CANCELLATION_REQUESTED) {
                eventService.handleOrderCancellationRequested(event.orderId());
            }
        } catch (Exception e) {
            log.error("Failed to process order event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

- [ ] **Step 5.11: Update SagaTopicsProperties**

Replace `payment-service/src/main/java/zen/labs/payment/infrastructure/config/SagaTopicsProperties.java`:

```java
package zen.labs.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String paymentEvents,
        String inventoryEvents,
        String shippingEvents,
        String orderEvents) {}
```

- [ ] **Step 5.12: Update KafkaConsumerConfiguration**

Replace `payment-service/src/main/java/zen/labs/payment/infrastructure/config/KafkaConsumerConfiguration.java`:

```java
package zen.labs.payment.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@EnableConfigurationProperties(SagaTopicsProperties.class)
public class KafkaConsumerConfiguration {}
```

- [ ] **Step 5.13: Update application.yml**

In `payment-service/src/main/resources/application.yml`, replace `application.saga.topics`:

```yaml
application:
  default-page-limit: 25
  saga:
    topics:
      payment-events: kafka-zen.lab-802.saga.payment.events
      inventory-events: kafka-zen.lab-802.saga.inventory.events
      shipping-events: kafka-zen.lab-802.saga.shipping.events
      order-events: kafka-zen.lab-802.saga.order.events
```

Apply the same to `payment-service/src/integrationTest/resources/application-test.yml` and `payment-service/src/test/resources/application-test.yml`.

- [ ] **Step 5.14: Build payment-service — expect GREEN**

```bash
./gradlew :payment-service:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5.15: Commit**

```bash
git add payment-service/
git commit -m "refactor(payment-service): replace command/reply with event-driven choreography"
```

---

## Task 6: Refactor shipping-service

**Files to delete:**
```
shipping-service/src/main/java/zen/labs/shipping/application/port/inbound/HandleShipmentCommandUseCase.java
shipping-service/src/main/java/zen/labs/shipping/application/port/outbound/SagaReplyPublisher.java
shipping-service/src/main/java/zen/labs/shipping/application/service/ShipmentSagaParticipant.java
shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommandListener.java
shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommand.java
shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommandItem.java
shipping-service/src/main/java/zen/labs/shipping/adapter/outbound/messaging/KafkaReplyPublisher.java
shipping-service/src/main/java/zen/labs/shipping/adapter/outbound/messaging/SagaReply.java

shipping-service/src/test/java/zen/labs/shipping/application/service/ShipmentSagaParticipantTest.java
shipping-service/src/test/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommandListenerTest.java
shipping-service/src/test/java/zen/labs/shipping/adapter/outbound/messaging/KafkaReplyPublisherTest.java
```

- [ ] **Step 6.1: Delete orchestration files**

```bash
rm shipping-service/src/main/java/zen/labs/shipping/application/port/inbound/HandleShipmentCommandUseCase.java \
   shipping-service/src/main/java/zen/labs/shipping/application/port/outbound/SagaReplyPublisher.java \
   shipping-service/src/main/java/zen/labs/shipping/application/service/ShipmentSagaParticipant.java \
   shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommandListener.java \
   shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommand.java \
   shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommandItem.java \
   shipping-service/src/main/java/zen/labs/shipping/adapter/outbound/messaging/KafkaReplyPublisher.java \
   shipping-service/src/main/java/zen/labs/shipping/adapter/outbound/messaging/SagaReply.java

rm shipping-service/src/test/java/zen/labs/shipping/application/service/ShipmentSagaParticipantTest.java \
   shipping-service/src/test/java/zen/labs/shipping/adapter/inbound/messaging/SagaCommandListenerTest.java \
   shipping-service/src/test/java/zen/labs/shipping/adapter/outbound/messaging/KafkaReplyPublisherTest.java
```

- [ ] **Step 6.2: Add CANCELLED to ShipmentStatus**

Open `shipping-service/src/main/java/zen/labs/shipping/domain/ShipmentStatus.java`.  
Add `CANCELLED` to the enum values.

- [ ] **Step 6.3: Remove sagaId from Shipment domain + persistence**

  - `shipping-service/.../domain/Shipment.java`: remove `sagaId` field
  - `shipping-service/.../persistence/entity/ShipmentJpaEntity.java`: remove `sagaId` column
  - `shipping-service/.../persistence/repository/ShipmentRepository.java`: rename `findBySagaId` → `findByOrderId`
  - `shipping-service/.../persistence/ShipmentJpaDataStore.java`: update call + toDomain/toEntity

- [ ] **Step 6.4: Add ShipmentDataStore.cancelByOrderId and findByOrderId**

Open `shipping-service/src/main/java/zen/labs/shipping/application/port/outbound/ShipmentDataStore.java`.  
Ensure it has `findByOrderId(String orderId)` (rename from `findBySagaId`). If a `cancelByOrderId` method would help encapsulation, add it; otherwise `findByOrderId` + `update` is fine.

- [ ] **Step 6.5: Add Flyway migration**

Create `shipping-service/src/main/resources/db/migration/V0003__drop-saga-id-from-shipment.sql`:

```sql
alter table shipping_service.shipment drop column if exists saga_id;
```

- [ ] **Step 6.6: Create ShippingEventPublisher port**

Create `shipping-service/src/main/java/zen/labs/shipping/application/port/outbound/ShippingEventPublisher.java`:

```java
package zen.labs.shipping.application.port.outbound;

public interface ShippingEventPublisher {
    void publishShipmentCreated(String orderId);
    void publishShipmentFailed(String orderId, String reason);
}
```

- [ ] **Step 6.7: Write ShipmentEventServiceTest first (TDD)**

Create `shipping-service/src/test/java/zen/labs/shipping/application/service/ShipmentEventServiceTest.java`:

```java
package zen.labs.shipping.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShipmentEventService")
class ShipmentEventServiceTest {

    @Mock private ShipmentDataStore shipmentDataStore;
    @Mock private ShippingEventPublisher eventPublisher;
    @InjectMocks private ShipmentEventService service;

    private static final String ORDER_ID = "order-001";
    static final int MAX_ADDRESS_LENGTH = 200;

    @Nested
    @DisplayName("handlePaymentProcessed")
    class HandlePaymentProcessed {

        @Test
        @DisplayName("should save shipment and publish SHIPMENT_CREATED when address within limit")
        void shouldCreateAndPublish_whenAddressWithinLimit() {
            String address = "123 Main St";
            when(shipmentDataStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handlePaymentProcessed(ORDER_ID, address);

            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentDataStore).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ShipmentStatus.CREATED);
            verify(eventPublisher).publishShipmentCreated(ORDER_ID);
        }

        @Test
        @DisplayName("should NOT save and publish SHIPMENT_FAILED when address exceeds limit")
        void shouldFailAndPublish_whenAddressTooLong() {
            String longAddress = "A".repeat(MAX_ADDRESS_LENGTH + 1);

            service.handlePaymentProcessed(ORDER_ID, longAddress);

            verify(shipmentDataStore, never()).save(any());
            verify(eventPublisher).publishShipmentFailed(any(), any());
        }
    }

    @Nested
    @DisplayName("handleOrderCancellationRequested")
    class HandleOrderCancellationRequested {

        @Test
        @DisplayName("should update shipment to CANCELLED when shipment exists")
        void shouldCancelShipment_whenExists() {
            var existing = Shipment.builder()
                    .orderId(ORDER_ID).shippingAddress("123 St")
                    .status(ShipmentStatus.CREATED).createdAt(Instant.now()).build();
            when(shipmentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(shipmentDataStore).update(existing.withStatus(ShipmentStatus.CANCELLED));
        }

        @Test
        @DisplayName("should do nothing when no shipment exists")
        void shouldDoNothing_whenNoShipment() {
            when(shipmentDataStore.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            service.handleOrderCancellationRequested(ORDER_ID);

            verify(shipmentDataStore, never()).update(any());
        }
    }
}
```

- [ ] **Step 6.8: Run test to confirm it fails**

```bash
./gradlew :shipping-service:test --tests "zen.labs.shipping.application.service.ShipmentEventServiceTest"
```

Expected: compilation failure.

- [ ] **Step 6.9: Implement ShipmentEventService**

Create `shipping-service/src/main/java/zen/labs/shipping/application/service/ShipmentEventService.java`:

```java
package zen.labs.shipping.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zen.labs.shipping.application.port.outbound.ShipmentDataStore;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;
import zen.labs.shipping.domain.Shipment;
import zen.labs.shipping.domain.ShipmentStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventService {

    static final int MAX_ADDRESS_LENGTH = 200;

    private final ShipmentDataStore shipmentDataStore;
    private final ShippingEventPublisher eventPublisher;

    @Transactional
    public void handlePaymentProcessed(String orderId, String shippingAddress) {
        if (shippingAddress.length() > MAX_ADDRESS_LENGTH) {
            log.warn("Shipment failed — address too long: orderId={}", orderId);
            eventPublisher.publishShipmentFailed(orderId,
                    "Shipping address exceeds maximum length of " + MAX_ADDRESS_LENGTH);
            return;
        }
        var shipment = Shipment.builder()
                .orderId(orderId).shippingAddress(shippingAddress)
                .status(ShipmentStatus.CREATED).createdAt(Instant.now())
                .build();
        shipmentDataStore.save(shipment);
        log.info("Shipment created: orderId={}", orderId);
        eventPublisher.publishShipmentCreated(orderId);
    }

    @Transactional
    public void handleOrderCancellationRequested(String orderId) {
        shipmentDataStore.findByOrderId(orderId).ifPresentOrElse(
                s -> {
                    shipmentDataStore.update(s.withStatus(ShipmentStatus.CANCELLED));
                    log.info("Shipment cancelled: orderId={}", orderId);
                },
                () -> log.debug("No shipment to cancel: orderId={}", orderId));
    }
}
```

- [ ] **Step 6.10: Run ShipmentEventServiceTest — expect PASS**

```bash
./gradlew :shipping-service:test --tests "zen.labs.shipping.application.service.ShipmentEventServiceTest"
```

Expected: BUILD SUCCESSFUL (4 tests passing)

- [ ] **Step 6.11: Implement KafkaShippingEventPublisher**

Create `shipping-service/src/main/java/zen/labs/shipping/adapter/outbound/messaging/KafkaShippingEventPublisher.java`:

```java
package zen.labs.shipping.adapter.outbound.messaging;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.adapters.common.kafka.SagaEventType;
import zen.labs.application.common.exceptions.ApplicationException;
import zen.labs.shipping.application.port.outbound.ShippingEventPublisher;

@Component
@Slf4j
public class KafkaShippingEventPublisher implements ShippingEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String shippingEventsTopic;

    public KafkaShippingEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${application.saga.topics.shipping-events}") String shippingEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.shippingEventsTopic = shippingEventsTopic;
    }

    @Override
    public void publishShipmentCreated(String orderId) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.SHIPMENT_CREATED,
                orderId, null, null, null, null));
    }

    @Override
    public void publishShipmentFailed(String orderId, String reason) {
        publish(orderId, new SagaEvent(
                UUID.randomUUID().toString(), SagaEventType.SHIPMENT_FAILED,
                orderId, reason, null, null, null));
    }

    private void publish(String key, SagaEvent event) {
        try {
            kafkaTemplate.send(shippingEventsTopic, key, jsonMapper.writeValueAsString(event));
            log.debug("Published {}: orderId={}", event.type(), event.orderId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish shipping event: " + event.type(), e);
        }
    }
}
```

- [ ] **Step 6.12: Create PaymentEventListener and OrderEventListener for shipping-service**

Create `shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/PaymentEventListener.java`:

```java
package zen.labs.shipping.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.shipping.application.service.ShipmentEventService;

@Component
@Slf4j
public class PaymentEventListener {

    private final ShipmentEventService eventService;
    private final JsonMapper jsonMapper;

    public PaymentEventListener(ShipmentEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}-payment")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == zen.labs.adapters.common.kafka.SagaEventType.PAYMENT_PROCESSED) {
                eventService.handlePaymentProcessed(event.orderId(), event.shippingAddress());
            }
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

Create `shipping-service/src/main/java/zen/labs/shipping/adapter/inbound/messaging/OrderEventListener.java`:

```java
package zen.labs.shipping.adapter.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.kafka.SagaEvent;
import zen.labs.shipping.application.service.ShipmentEventService;

@Component
@Slf4j
public class OrderEventListener {

    private final ShipmentEventService eventService;
    private final JsonMapper jsonMapper;

    public OrderEventListener(ShipmentEventService eventService, JsonMapper jsonMapper) {
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${application.saga.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}-order")
    public void onEvent(@Payload String payload, Acknowledgment ack) {
        try {
            var event = jsonMapper.readValue(payload, SagaEvent.class);
            MDC.put("orderId", event.orderId());
            if (event.type() == zen.labs.adapters.common.kafka.SagaEventType.ORDER_CANCELLATION_REQUESTED) {
                eventService.handleOrderCancellationRequested(event.orderId());
            }
        } catch (Exception e) {
            log.error("Failed to process order event: {}", payload, e);
        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
```

- [ ] **Step 6.13: Update SagaTopicsProperties**

Replace `shipping-service/src/main/java/zen/labs/shipping/config/SagaTopicsProperties.java`:

```java
package zen.labs.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("application.saga.topics")
public record SagaTopicsProperties(
        String shippingEvents,
        String paymentEvents,
        String orderEvents) {}
```

- [ ] **Step 6.14: Update KafkaConsumerConfiguration**

Replace `shipping-service/src/main/java/zen/labs/shipping/config/KafkaConsumerConfiguration.java`:

```java
package zen.labs.shipping.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@EnableConfigurationProperties(SagaTopicsProperties.class)
public class KafkaConsumerConfiguration {}
```

- [ ] **Step 6.15: Update application.yml**

In `shipping-service/src/main/resources/application.yml`, replace `application.saga.topics`:

```yaml
application:
  default-page-limit: 25
  saga:
    topics:
      shipping-events: kafka-zen.lab-802.saga.shipping.events
      payment-events: kafka-zen.lab-802.saga.payment.events
      order-events: kafka-zen.lab-802.saga.order.events
```

Apply the same to `shipping-service/src/integrationTest/resources/application-test.yml` and `shipping-service/src/test/resources/application-test.yml`.

- [ ] **Step 6.16: Build shipping-service — expect GREEN**

```bash
./gradlew :shipping-service:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6.17: Full project build — expect GREEN**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL — all modules compile, all unit tests pass.

- [ ] **Step 6.18: Commit**

```bash
git add shipping-service/
git commit -m "refactor(shipping-service): replace command/reply with event-driven choreography"
```

---

## Missing payload: PAYMENT_PROCESSED must carry shippingAddress

The `PAYMENT_PROCESSED` event published by payment-service must carry `shippingAddress` so shipping-service can create the shipment. Follow the event-carried state transfer pattern:

1. `KafkaInventoryEventPublisher.publishInventoryReserved` must echo `amount` (received in ORDER_PLACED) in the `INVENTORY_RESERVED` event.
2. `KafkaPaymentEventPublisher.publishPaymentProcessed` must echo `shippingAddress` (received in INVENTORY_RESERVED→ passed through) in `PAYMENT_PROCESSED`.

This means:
- `InventoryEventPublisher.publishInventoryReserved(String orderId, BigDecimal amount)` — add `amount` param
- `InventoryEventService.handleOrderPlaced(...)` must receive `amount` from the ORDER_PLACED event and pass it to the publisher
- `PaymentEventPublisher.publishPaymentProcessed(String orderId, String shippingAddress)` — add `shippingAddress` param
- `PaymentEventService.handleInventoryReserved(orderId, amount)` must also receive `shippingAddress` from the INVENTORY_RESERVED event and pass it to the publisher

The `SagaEvent.amount` field carries the amount through `INVENTORY_RESERVED`. For `shippingAddress`, the ORDER_PLACED event carries it, but it must also be echoed by inventory in `INVENTORY_RESERVED` so payment-service has it when publishing `PAYMENT_PROCESSED`.

**Apply these param additions to:** `InventoryEventPublisher`, `InventoryEventService.handleOrderPlaced`, `InventoryEventService` call to publisher, `KafkaInventoryEventPublisher.publishInventoryReserved`, `PaymentEventPublisher.publishPaymentProcessed`, `PaymentEventService.handleInventoryReserved`, and the corresponding tests.

Update tests to pass and verify the extra params at each step.

---

## Final verification

- [ ] Run full build

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL with all unit tests green.

- [ ] Run ArchUnit tests for all four services

```bash
./gradlew :order-service:test --tests "*ArchitectureTest"
./gradlew :inventory-service:test --tests "*ArchitectureTest"
./gradlew :payment-service:test --tests "*ArchitectureTest"
./gradlew :shipping-service:test --tests "*ArchitectureTest"
```

Expected: all pass — no layer violations introduced.
