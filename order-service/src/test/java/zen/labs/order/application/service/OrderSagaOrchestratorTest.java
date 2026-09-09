/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.order.application.port.inbound.PlaceOrderCommand;
import zen.labs.order.application.port.outbound.IdempotencyKeyStore;
import zen.labs.order.application.port.outbound.OrderDataStore;
import zen.labs.order.application.port.outbound.SagaCommandPublisher;
import zen.labs.order.application.port.outbound.SagaStateDataStore;
import zen.labs.order.domain.Order;
import zen.labs.order.domain.OrderItem;
import zen.labs.order.domain.OrderStatus;
import zen.labs.order.domain.SagaState;
import zen.labs.order.domain.SagaStatus;
import zen.labs.order.domain.SagaStep;

/**
 * Unit tests for {@link OrderSagaOrchestrator}.
 *
 * <p>No Spring context is loaded; all dependencies are mocked with Mockito.
 * Tests follow the Given-When-Then (Arrange-Act-Assert) structure and are
 * organised by public method using {@code @Nested} classes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaOrchestrator")
@SuppressWarnings("java:S125")
class OrderSagaOrchestratorTest {

    // -------------------------------------------------------------------------
    // Test constants
    // -------------------------------------------------------------------------
    private static final String ORDER_RESOURCE_ID = "order-001";
    private static final String SAGA_ID = "saga-001";
    private static final String CUSTOMER_ID = "customer-001";
    private static final String SHIPPING_ADDRESS = "123 Main St, Springfield";
    private static final String PRODUCT_ID = "product-001";
    private static final String FAILURE_REASON = "Insufficient stock";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    // -------------------------------------------------------------------------
    // Mocks and subject under test
    // -------------------------------------------------------------------------
    @Mock
    private OrderDataStore orderDataStore;

    @Mock
    private SagaStateDataStore sagaStateDataStore;

    @Mock
    private SagaCommandPublisher commandPublisher;

    @Mock
    private ResourceIdSupplier resourceIdSupplier;

    @Mock
    private IdempotencyKeyStore idempotencyKeyStore;

    @InjectMocks
    private OrderSagaOrchestrator orchestrator;

    // -------------------------------------------------------------------------
    // Shared test fixtures
    // -------------------------------------------------------------------------

    /** Builds a minimal {@link PlaceOrderCommand} with one item. */
    private PlaceOrderCommand buildPlaceOrderCommand() {
        var item = new PlaceOrderCommand.Item(PRODUCT_ID, 2, new BigDecimal("25.00"));
        return new PlaceOrderCommand(null, CUSTOMER_ID, List.of(item), SHIPPING_ADDRESS);
    }

    /** Builds a persisted {@link Order} that mirrors what the data-store would return. */
    private Order buildPersistedOrder() {
        return Order.builder()
                .resourceId(ORDER_RESOURCE_ID)
                .customerId(CUSTOMER_ID)
                .items(List.of(new OrderItem(PRODUCT_ID, 2, new BigDecimal("25.00"))))
                .shippingAddress(SHIPPING_ADDRESS)
                .status(OrderStatus.CONFIRMING)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    /** Builds a persisted {@link SagaState} in the STARTED/RESERVE_INVENTORY state. */
    private SagaState buildStartedSagaState() {
        return SagaState.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_RESOURCE_ID)
                .status(SagaStatus.STARTED)
                .currentStep(SagaStep.RESERVE_INVENTORY)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private SagaState buildInventoryReservedSagaState() {
        return SagaState.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_RESOURCE_ID)
                .status(SagaStatus.INVENTORY_RESERVED)
                .currentStep(SagaStep.PROCESS_PAYMENT)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private SagaState buildPaymentProcessedSagaState() {
        return SagaState.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_RESOURCE_ID)
                .status(SagaStatus.PAYMENT_PROCESSED)
                .currentStep(SagaStep.CREATE_SHIPMENT)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    // =========================================================================
    // placeOrder
    // =========================================================================

    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @BeforeEach
        void stubResourceIdSupplier() {
            // First call produces the order resource ID; second call produces the saga ID.
            when(resourceIdSupplier.nextResourceId())
                    .thenReturn(ORDER_RESOURCE_ID)
                    .thenReturn(SAGA_ID);
        }

        @Test
        @DisplayName("should persist order with CONFIRMING status and return it")
        void shouldPersistOrderWithConfirmingStatusAndReturnIt() {
            // Given
            var cmd = buildPlaceOrderCommand();
            var persistedOrder = buildPersistedOrder();
            when(orderDataStore.save(any(Order.class))).thenReturn(persistedOrder);
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            var result = orchestrator.placeOrder(cmd);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getResourceId()).isEqualTo(ORDER_RESOURCE_ID);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        }

        @Test
        @DisplayName("should save order with correct customer and shipping details")
        void shouldSaveOrderWithCorrectCustomerAndShippingDetails() {
            // Given
            var cmd = buildPlaceOrderCommand();
            var persistedOrder = buildPersistedOrder();
            when(orderDataStore.save(any(Order.class))).thenReturn(persistedOrder);
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            orchestrator.placeOrder(cmd);

            // Then – capture what was actually passed to the data-store
            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).save(orderCaptor.capture());
            var capturedOrder = orderCaptor.getValue();

            assertThat(capturedOrder.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(capturedOrder.getShippingAddress()).isEqualTo(SHIPPING_ADDRESS);
            assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            assertThat(capturedOrder.getItems()).hasSize(1);
            assertThat(capturedOrder.getItems().get(0).productId()).isEqualTo(PRODUCT_ID);
        }

        @Test
        @DisplayName("should save saga state with STARTED status and RESERVE_INVENTORY step")
        void shouldSaveSagaStateWithStartedStatusAndReserveInventoryStep() {
            // Given
            var cmd = buildPlaceOrderCommand();
            when(orderDataStore.save(any(Order.class))).thenReturn(buildPersistedOrder());
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            orchestrator.placeOrder(cmd);

            // Then – capture what was passed to the saga data-store
            var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateDataStore).save(sagaCaptor.capture());
            var capturedSaga = sagaCaptor.getValue();

            assertThat(capturedSaga.getSagaId()).isEqualTo(SAGA_ID);
            assertThat(capturedSaga.getOrderId()).isEqualTo(ORDER_RESOURCE_ID);
            assertThat(capturedSaga.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(capturedSaga.getCurrentStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
        }

        @Test
        @DisplayName("should publish reserveInventory command after saving order and saga")
        void shouldPublishReserveInventoryCommand() {
            // Given
            var cmd = buildPlaceOrderCommand();
            var persistedOrder = buildPersistedOrder();
            when(orderDataStore.save(any(Order.class))).thenReturn(persistedOrder);
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            orchestrator.placeOrder(cmd);

            // Then
            verify(commandPublisher).reserveInventory(SAGA_ID, ORDER_RESOURCE_ID, persistedOrder.getItems());
        }

        @Test
        @DisplayName("should map PlaceOrderCommand items to OrderItems correctly")
        void shouldMapCommandItemsToOrderItems() {
            // Given
            var item1 = new PlaceOrderCommand.Item("prod-A", 3, new BigDecimal("10.00"));
            var item2 = new PlaceOrderCommand.Item("prod-B", 1, new BigDecimal("50.00"));
            var cmd = new PlaceOrderCommand(null, CUSTOMER_ID, List.of(item1, item2), SHIPPING_ADDRESS);

            when(orderDataStore.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            orchestrator.placeOrder(cmd);

            // Then
            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).save(orderCaptor.capture());
            var items = orderCaptor.getValue().getItems();

            assertThat(items).hasSize(2);
            assertThat(items.get(0).productId()).isEqualTo("prod-A");
            assertThat(items.get(0).quantity()).isEqualTo(3);
            assertThat(items.get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(items.get(1).productId()).isEqualTo("prod-B");
            assertThat(items.get(1).quantity()).isEqualTo(1);
            assertThat(items.get(1).unitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
        }
    }

    // =========================================================================
    // placeOrder — idempotency
    // =========================================================================

    @Nested
    @DisplayName("placeOrder — idempotency")
    class PlaceOrderIdempotency {

        private static final String IDEMPOTENCY_KEY = "idem-key-abc";

        @Test
        @DisplayName("should return existing order when key is already known")
        void shouldReturnExistingOrderWhenKeyAlreadyKnown() {
            // Given
            var cmd = new PlaceOrderCommand(
                    IDEMPOTENCY_KEY,
                    CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item(PRODUCT_ID, 1, new BigDecimal("10.00"))),
                    SHIPPING_ADDRESS);
            var existingOrder = buildPersistedOrder();
            when(idempotencyKeyStore.findOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.of(ORDER_RESOURCE_ID));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(existingOrder));

            // When
            var result = orchestrator.placeOrder(cmd);

            // Then
            assertThat(result).isSameAs(existingOrder);
            verify(orderDataStore, never()).save(any());
            verify(commandPublisher, never()).reserveInventory(any(), any(), any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when key maps to missing order")
        void shouldThrowWhenKeyFoundButOrderMissing() {
            // Given
            var cmd = new PlaceOrderCommand(
                    IDEMPOTENCY_KEY,
                    CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item(PRODUCT_ID, 1, new BigDecimal("10.00"))),
                    SHIPPING_ADDRESS);
            when(idempotencyKeyStore.findOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.of(ORDER_RESOURCE_ID));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.placeOrder(cmd))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should save idempotency key before publishing reserveInventory command")
        void shouldSaveIdempotencyKeyBeforePublishingCommand() {
            // Given
            when(resourceIdSupplier.nextResourceId())
                    .thenReturn(ORDER_RESOURCE_ID)
                    .thenReturn(SAGA_ID);
            var cmd = new PlaceOrderCommand(
                    IDEMPOTENCY_KEY,
                    CUSTOMER_ID,
                    List.of(new PlaceOrderCommand.Item(PRODUCT_ID, 1, new BigDecimal("10.00"))),
                    SHIPPING_ADDRESS);
            when(idempotencyKeyStore.findOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(orderDataStore.save(any(Order.class))).thenReturn(buildPersistedOrder());
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            orchestrator.placeOrder(cmd);

            // Then — key must be persisted before the Kafka command fires so that a crash
            // between publish and save cannot create a second saga on retry.
            var inOrder = org.mockito.Mockito.inOrder(idempotencyKeyStore, commandPublisher);
            inOrder.verify(idempotencyKeyStore).save(IDEMPOTENCY_KEY, ORDER_RESOURCE_ID);
            inOrder.verify(commandPublisher)
                    .reserveInventory(
                            SAGA_ID, ORDER_RESOURCE_ID, buildPersistedOrder().getItems());
        }

        @Test
        @DisplayName("should not save idempotency key when command has no key")
        void shouldNotSaveIdempotencyKeyWhenCommandHasNoKey() {
            // Given
            when(resourceIdSupplier.nextResourceId())
                    .thenReturn(ORDER_RESOURCE_ID)
                    .thenReturn(SAGA_ID);
            var cmd = buildPlaceOrderCommand(); // idempotencyKey = null
            when(orderDataStore.save(any(Order.class))).thenReturn(buildPersistedOrder());
            when(sagaStateDataStore.save(any(SagaState.class))).thenReturn(buildStartedSagaState());

            // When
            orchestrator.placeOrder(cmd);

            // Then
            verify(idempotencyKeyStore, never()).findOrderId(any());
            verify(idempotencyKeyStore, never()).save(any(), any());
        }
    }

    // =========================================================================
    // onInventoryReply
    // =========================================================================

    @Nested
    @DisplayName("onInventoryReply")
    class OnInventoryReply {

        private SagaState startedSaga;

        @BeforeEach
        void stubCommonDependencies() {
            startedSaga = buildStartedSagaState();
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(startedSaga));
        }

        @Nested
        @DisplayName("when success=true")
        class WhenSuccessful {

            @BeforeEach
            void stubOrder() {
                when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            }

            @Test
            @DisplayName("should update saga to INVENTORY_RESERVED / PROCESS_PAYMENT")
            void shouldUpdateSagaToInventoryReserved() {
                // When
                orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
                verify(sagaStateDataStore).update(sagaCaptor.capture());
                var updated = sagaCaptor.getValue();

                assertThat(updated.getStatus()).isEqualTo(SagaStatus.INVENTORY_RESERVED);
                assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
            }

            @Test
            @DisplayName("should publish processPayment command with order total amount")
            void shouldPublishProcessPaymentCommand() {
                // Given – total = 2 * 25.00 = 50.00
                var expectedTotal = new BigDecimal("50.00");

                // When
                orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                verify(commandPublisher).processPayment(SAGA_ID, ORDER_RESOURCE_ID, expectedTotal);
            }

            @Test
            @DisplayName("should not publish any compensation commands")
            void shouldNotPublishCompensationCommands() {
                // When
                orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                verify(commandPublisher, never()).releaseInventory(any(), any());
                verify(commandPublisher, never()).refundPayment(any(), any());
            }
        }

        @Nested
        @DisplayName("when success=false")
        class WhenFailed {

            @BeforeEach
            void stubOrder() {
                when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            }

            @Test
            @DisplayName("should update saga to FAILED / DONE")
            void shouldUpdateSagaToFailed() {
                // When
                orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
                verify(sagaStateDataStore).update(sagaCaptor.capture());
                var updated = sagaCaptor.getValue();

                assertThat(updated.getStatus()).isEqualTo(SagaStatus.FAILED);
                assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.DONE);
            }

            @Test
            @DisplayName("should update order status to CANCELLED")
            void shouldUpdateOrderStatusToCancelled() {
                // When
                orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                var orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderDataStore).update(orderCaptor.capture());
                assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            }

            @Test
            @DisplayName("should not publish any forward commands")
            void shouldNotPublishForwardCommands() {
                // When
                orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                verify(commandPublisher, never()).processPayment(any(), any(), any());
                verify(commandPublisher, never()).createShipment(any(), any(), any());
            }
        }
    }

    // =========================================================================
    // onPaymentReply
    // =========================================================================

    @Nested
    @DisplayName("onPaymentReply")
    class OnPaymentReply {

        private SagaState inventoryReservedSaga;

        @BeforeEach
        void stubCommonDependencies() {
            inventoryReservedSaga = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_RESOURCE_ID)
                    .status(SagaStatus.INVENTORY_RESERVED)
                    .currentStep(SagaStep.PROCESS_PAYMENT)
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(inventoryReservedSaga));
        }

        @Nested
        @DisplayName("when success=true")
        class WhenSuccessful {

            @BeforeEach
            void stubOrder() {
                when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            }

            @Test
            @DisplayName("should update saga to PAYMENT_PROCESSED / CREATE_SHIPMENT")
            void shouldUpdateSagaToPaymentProcessed() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
                verify(sagaStateDataStore).update(sagaCaptor.capture());
                var updated = sagaCaptor.getValue();

                assertThat(updated.getStatus()).isEqualTo(SagaStatus.PAYMENT_PROCESSED);
                assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.CREATE_SHIPMENT);
            }

            @Test
            @DisplayName("should publish createShipment command with order shipping address")
            void shouldPublishCreateShipmentCommand() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                verify(commandPublisher).createShipment(SAGA_ID, ORDER_RESOURCE_ID, SHIPPING_ADDRESS);
            }

            @Test
            @DisplayName("should not publish any compensation commands")
            void shouldNotPublishCompensationCommands() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                verify(commandPublisher, never()).releaseInventory(any(), any());
                verify(commandPublisher, never()).refundPayment(any(), any());
            }
        }

        @Nested
        @DisplayName("when success=false")
        class WhenFailed {

            @BeforeEach
            void stubOrder() {
                when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            }

            @Test
            @DisplayName("should update saga to COMPENSATING / COMPENSATE_INVENTORY")
            void shouldUpdateSagaToCompensatingInventory() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
                verify(sagaStateDataStore).update(sagaCaptor.capture());
                var updated = sagaCaptor.getValue();

                assertThat(updated.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
                assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.COMPENSATE_INVENTORY);
            }

            @Test
            @DisplayName("should update order status to CANCELLING")
            void shouldUpdateOrderStatusToCancelling() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                var orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderDataStore).update(orderCaptor.capture());
                assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLING);
            }

            @Test
            @DisplayName("should publish releaseInventory compensation command")
            void shouldPublishReleaseInventoryCommand() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                verify(commandPublisher).releaseInventory(SAGA_ID, ORDER_RESOURCE_ID);
            }

            @Test
            @DisplayName("should not publish createShipment command")
            void shouldNotPublishCreateShipmentCommand() {
                // When
                orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                verify(commandPublisher, never()).createShipment(any(), any(), any());
            }
        }
    }

    // =========================================================================
    // onShipmentReply
    // =========================================================================

    @Nested
    @DisplayName("onShipmentReply")
    class OnShipmentReply {

        private SagaState paymentProcessedSaga;

        @BeforeEach
        void stubCommonDependencies() {
            paymentProcessedSaga = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_RESOURCE_ID)
                    .status(SagaStatus.PAYMENT_PROCESSED)
                    .currentStep(SagaStep.CREATE_SHIPMENT)
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(paymentProcessedSaga));
        }

        @Nested
        @DisplayName("when success=true")
        class WhenSuccessful {

            @BeforeEach
            void stubOrder() {
                when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            }

            @Test
            @DisplayName("should update saga to COMPLETED / DONE")
            void shouldUpdateSagaToCompleted() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
                verify(sagaStateDataStore).update(sagaCaptor.capture());
                var updated = sagaCaptor.getValue();

                assertThat(updated.getStatus()).isEqualTo(SagaStatus.COMPLETED);
                assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.DONE);
            }

            @Test
            @DisplayName("should update order status to CONFIRMED")
            void shouldUpdateOrderStatusToConfirmed() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                var orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderDataStore).update(orderCaptor.capture());
                assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            }

            @Test
            @DisplayName("should not publish any compensation commands")
            void shouldNotPublishCompensationCommands() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null);

                // Then
                verify(commandPublisher, never()).refundPayment(any(), any());
                verify(commandPublisher, never()).releaseInventory(any(), any());
            }
        }

        @Nested
        @DisplayName("when success=false")
        class WhenFailed {

            @BeforeEach
            void stubOrder() {
                when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            }

            @Test
            @DisplayName("should update saga to COMPENSATING / COMPENSATE_PAYMENT")
            void shouldUpdateSagaToCompensatingPayment() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
                verify(sagaStateDataStore).update(sagaCaptor.capture());
                var updated = sagaCaptor.getValue();

                assertThat(updated.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
                assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.COMPENSATE_PAYMENT);
            }

            @Test
            @DisplayName("should update order status to CANCELLING")
            void shouldUpdateOrderStatusToCancelling() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                var orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderDataStore).update(orderCaptor.capture());
                assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLING);
            }

            @Test
            @DisplayName("should publish refundPayment compensation command")
            void shouldPublishRefundPaymentCommand() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then
                verify(commandPublisher).refundPayment(SAGA_ID, ORDER_RESOURCE_ID);
            }

            @Test
            @DisplayName("should not update order to CONFIRMED")
            void shouldNotConfirmOrder() {
                // When
                orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

                // Then – the update call targets CANCELLING, not CONFIRMED
                var orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderDataStore).update(orderCaptor.capture());
                assertThat(orderCaptor.getValue().getStatus()).isNotEqualTo(OrderStatus.CONFIRMED);
            }
        }
    }

    // =========================================================================
    // onInventoryReleasedReply
    // =========================================================================

    @Nested
    @DisplayName("onInventoryReleasedReply")
    class OnInventoryReleasedReply {

        @BeforeEach
        void stubDependencies() {
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildStartedSagaState()));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
        }

        @Test
        @DisplayName("should update saga to FAILED / DONE")
        void shouldUpdateSagaToFailed() {
            // When
            orchestrator.onInventoryReleasedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then
            var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateDataStore).update(sagaCaptor.capture());
            var updated = sagaCaptor.getValue();

            assertThat(updated.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.DONE);
        }

        @Test
        @DisplayName("should update order status to CANCELLED")
        void shouldUpdateOrderStatusToCancelled() {
            // When
            orchestrator.onInventoryReleasedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then
            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should not publish any further commands")
        void shouldNotPublishAnyFurtherCommands() {
            // When
            orchestrator.onInventoryReleasedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then
            verify(commandPublisher, never()).reserveInventory(any(), any(), any());
            verify(commandPublisher, never()).processPayment(any(), any(), any());
            verify(commandPublisher, never()).createShipment(any(), any(), any());
            verify(commandPublisher, never()).releaseInventory(any(), any());
            verify(commandPublisher, never()).refundPayment(any(), any());
        }
    }

    // =========================================================================
    // onPaymentRefundedReply
    // =========================================================================

    @Nested
    @DisplayName("onPaymentRefundedReply")
    class OnPaymentRefundedReply {

        @BeforeEach
        void stubDependencies() {
            var compensatingSaga = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_RESOURCE_ID)
                    .status(SagaStatus.COMPENSATING)
                    .currentStep(SagaStep.COMPENSATE_PAYMENT)
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(compensatingSaga));
        }

        @Test
        @DisplayName("should update saga to COMPENSATING / COMPENSATE_INVENTORY")
        void shouldUpdateSagaToCompensatingInventory() {
            // When
            orchestrator.onPaymentRefundedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then
            var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateDataStore).update(sagaCaptor.capture());
            var updated = sagaCaptor.getValue();

            assertThat(updated.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.COMPENSATE_INVENTORY);
        }

        @Test
        @DisplayName("should publish releaseInventory command")
        void shouldPublishReleaseInventoryCommand() {
            // When
            orchestrator.onPaymentRefundedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then
            verify(commandPublisher).releaseInventory(SAGA_ID, ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should not update order status directly")
        void shouldNotUpdateOrderStatus() {
            // When
            orchestrator.onPaymentRefundedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then – onPaymentRefundedReply only updates the saga and issues a command;
            // it does not touch the order data-store.
            verify(orderDataStore, never()).update(any());
            verify(orderDataStore, never()).findByResourceId(any());
        }

        @Test
        @DisplayName("should not publish refundPayment again")
        void shouldNotPublishRefundPaymentAgain() {
            // When
            orchestrator.onPaymentRefundedReply(SAGA_ID, ORDER_RESOURCE_ID);

            // Then
            verify(commandPublisher, never()).refundPayment(any(), any());
        }
    }

    // =========================================================================
    // cancelOrder
    // =========================================================================

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should compensate inventory and set order CANCELLING when saga is INVENTORY_RESERVED")
        void shouldCompensateInventory_whenSagaIsInventoryReserved() {
            // Given
            var saga = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_RESOURCE_ID)
                    .status(SagaStatus.INVENTORY_RESERVED)
                    .currentStep(SagaStep.PROCESS_PAYMENT)
                    .startedAt(FIXED_INSTANT)
                    .updatedAt(FIXED_INSTANT)
                    .build();
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            when(sagaStateDataStore.findByOrderId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(saga));
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(saga));

            // When
            orchestrator.cancelOrder(ORDER_RESOURCE_ID);

            // Then
            var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateDataStore).update(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(sagaCaptor.getValue().getCurrentStep()).isEqualTo(SagaStep.COMPENSATE_INVENTORY);

            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLING);

            verify(commandPublisher).releaseInventory(SAGA_ID, ORDER_RESOURCE_ID);
            verify(commandPublisher, never()).refundPayment(any(), any());
        }

        @Test
        @DisplayName("should compensate payment and set order CANCELLING when saga is PAYMENT_PROCESSED")
        void shouldCompensatePayment_whenSagaIsPaymentProcessed() {
            // Given
            var saga = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_RESOURCE_ID)
                    .status(SagaStatus.PAYMENT_PROCESSED)
                    .currentStep(SagaStep.CREATE_SHIPMENT)
                    .startedAt(FIXED_INSTANT)
                    .updatedAt(FIXED_INSTANT)
                    .build();
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            when(sagaStateDataStore.findByOrderId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(saga));
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(saga));

            // When
            orchestrator.cancelOrder(ORDER_RESOURCE_ID);

            // Then
            var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateDataStore).update(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(sagaCaptor.getValue().getCurrentStep()).isEqualTo(SagaStep.COMPENSATE_PAYMENT);

            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderDataStore).update(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLING);

            verify(commandPublisher).refundPayment(SAGA_ID, ORDER_RESOURCE_ID);
            verify(commandPublisher, never()).releaseInventory(any(), any());
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when order status is not CONFIRMING")
        void shouldThrow_whenOrderNotConfirming() {
            // Given — order already CONFIRMED
            var confirmedOrder = Order.builder()
                    .resourceId(ORDER_RESOURCE_ID)
                    .customerId(CUSTOMER_ID)
                    .items(List.of())
                    .shippingAddress(SHIPPING_ADDRESS)
                    .status(OrderStatus.CONFIRMED)
                    .createdAt(FIXED_INSTANT)
                    .build();
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(confirmedOrder));

            // When / Then
            assertThatThrownBy(() -> orchestrator.cancelOrder(ORDER_RESOURCE_ID))
                    .isInstanceOf(UnprocessableRequestException.class)
                    .hasMessageContaining("CONFIRMED");
        }

        @Test
        @DisplayName("should throw UnprocessableRequestException when saga status is not compensatable")
        void shouldThrow_whenSagaStatusNotCompensatable() {
            // Given — saga is still STARTED (reservation in-flight)
            var saga = SagaState.builder()
                    .sagaId(SAGA_ID)
                    .orderId(ORDER_RESOURCE_ID)
                    .status(SagaStatus.STARTED)
                    .currentStep(SagaStep.RESERVE_INVENTORY)
                    .startedAt(FIXED_INSTANT)
                    .updatedAt(FIXED_INSTANT)
                    .build();
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            when(sagaStateDataStore.findByOrderId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(saga));

            // When / Then
            assertThatThrownBy(() -> orchestrator.cancelOrder(ORDER_RESOURCE_ID))
                    .isInstanceOf(UnprocessableRequestException.class)
                    .hasMessageContaining("STARTED");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when saga is not found for the order")
        void shouldThrow_whenSagaNotFound() {
            // Given
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));
            when(sagaStateDataStore.findByOrderId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.cancelOrder(ORDER_RESOURCE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found")
        void shouldThrow_whenOrderNotFound() {
            // Given
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.cancelOrder(ORDER_RESOURCE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }
    }

    // =========================================================================
    // requireSaga — ResourceNotFoundException propagation
    // =========================================================================

    @Nested
    @DisplayName("requireSaga — ResourceNotFoundException")
    class RequireSaga {

        @Test
        @DisplayName("should throw ResourceNotFoundException when saga is not found during onInventoryReply")
        void shouldThrowWhenSagaNotFoundOnInventoryReply() {
            // Given
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SAGA_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when saga is not found during onPaymentReply")
        void shouldThrowWhenSagaNotFoundOnPaymentReply() {
            // Given
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SAGA_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when saga is not found during onShipmentReply")
        void shouldThrowWhenSagaNotFoundOnShipmentReply() {
            // Given
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SAGA_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when saga is not found during onInventoryReleasedReply")
        void shouldThrowWhenSagaNotFoundOnInventoryReleasedReply() {
            // Given
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onInventoryReleasedReply(SAGA_ID, ORDER_RESOURCE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SAGA_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when saga is not found during onPaymentRefundedReply")
        void shouldThrowWhenSagaNotFoundOnPaymentRefundedReply() {
            // Given
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onPaymentRefundedReply(SAGA_ID, ORDER_RESOURCE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SAGA_ID);
        }
    }

    // =========================================================================
    // requireOrder — ResourceNotFoundException propagation
    // =========================================================================

    @Nested
    @DisplayName("requireOrder — ResourceNotFoundException")
    class RequireOrder {

        @BeforeEach
        void stubSagaFound() {
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildStartedSagaState()));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onInventoryReply (success)")
        void shouldThrowWhenOrderNotFoundOnInventoryReply() {
            // Given
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onInventoryReply (failure)")
        void shouldThrowWhenOrderNotFoundOnInventoryReplyFailure() {
            // Given
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onPaymentReply (success)")
        void shouldThrowWhenOrderNotFoundOnPaymentReply() {
            // Given — saga must be INVENTORY_RESERVED for the PAYMENT_PROCESSED transition to be valid
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildInventoryReservedSagaState()));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onPaymentReply (failure)")
        void shouldThrowWhenOrderNotFoundOnPaymentReplyFailure() {
            // Given — saga must be INVENTORY_RESERVED for the COMPENSATING transition to be valid
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildInventoryReservedSagaState()));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onPaymentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onShipmentReply (success)")
        void shouldThrowWhenOrderNotFoundOnShipmentReplySuccess() {
            // Given — saga must be PAYMENT_PROCESSED for the COMPLETED transition to be valid
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildPaymentProcessedSagaState()));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, true, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onShipmentReply (failure)")
        void shouldThrowWhenOrderNotFoundOnShipmentReplyFailure() {
            // Given — saga must be PAYMENT_PROCESSED for the COMPENSATING transition to be valid
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildPaymentProcessedSagaState()));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onShipmentReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order is not found during onInventoryReleasedReply")
        void shouldThrowWhenOrderNotFoundOnInventoryReleasedReply() {
            // Given
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orchestrator.onInventoryReleasedReply(SAGA_ID, ORDER_RESOURCE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORDER_RESOURCE_ID);
        }
    }

    // =========================================================================
    // Saga updatedAt timestamp
    // =========================================================================

    @Nested
    @DisplayName("saga updatedAt timestamp")
    class SagaUpdatedAtTimestamp {

        @Test
        @DisplayName("should set updatedAt to a non-null recent instant when updating saga")
        void shouldSetUpdatedAtOnSagaUpdate() {
            // Given
            var beforeCall = Instant.parse("2026-01-01T00:00:00Z");
            when(sagaStateDataStore.findBySagaId(SAGA_ID)).thenReturn(Optional.of(buildStartedSagaState()));
            when(orderDataStore.findByResourceId(ORDER_RESOURCE_ID)).thenReturn(Optional.of(buildPersistedOrder()));

            // When
            orchestrator.onInventoryReply(SAGA_ID, ORDER_RESOURCE_ID, false, FAILURE_REASON);

            // Then
            var sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
            verify(sagaStateDataStore).update(sagaCaptor.capture());
            var updatedAt = sagaCaptor.getValue().getUpdatedAt();

            assertThat(updatedAt).isNotNull().isAfterOrEqualTo(beforeCall);
        }
    }
}
