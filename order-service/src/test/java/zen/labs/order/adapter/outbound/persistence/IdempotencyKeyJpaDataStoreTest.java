/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import zen.labs.order.adapter.outbound.persistence.entity.IdempotencyKeyJpaEntity;
import zen.labs.order.adapter.outbound.persistence.repository.IdempotencyKeyRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyKeyJpaDataStore")
class IdempotencyKeyJpaDataStoreTest {

    private static final String KEY = "idem-key-abc";
    private static final String ORDER_ID = "order-001";

    @Mock
    private IdempotencyKeyRepository repository;

    @InjectMocks
    private IdempotencyKeyJpaDataStore dataStore;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should persist entity with correct key and orderId")
        void shouldPersistEntityWithCorrectKeyAndOrderId() {
            // When
            dataStore.save(KEY, ORDER_ID);

            // Then
            var captor = ArgumentCaptor.forClass(IdempotencyKeyJpaEntity.class);
            verify(repository).save(captor.capture());
            var entity = captor.getValue();

            assertThat(entity.getKey()).isEqualTo(KEY);
            assertThat(entity.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(entity.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should swallow DataIntegrityViolationException on duplicate key")
        void shouldSwallowDataIntegrityViolationOnDuplicateKey() {
            // Given
            doThrow(new DataIntegrityViolationException("duplicate key"))
                    .when(repository)
                    .save(any(IdempotencyKeyJpaEntity.class));

            // When — must not throw
            dataStore.save(KEY, ORDER_ID);

            // Then — exception was swallowed; repository.save was called
            verify(repository).save(any(IdempotencyKeyJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("findOrderId")
    class FindOrderId {

        @Test
        @DisplayName("should return orderId when key exists")
        void shouldReturnOrderIdWhenKeyExists() {
            // Given
            var entity = new IdempotencyKeyJpaEntity();
            entity.setKey(KEY);
            entity.setOrderId(ORDER_ID);
            when(repository.findByKey(KEY)).thenReturn(Optional.of(entity));

            // When
            var result = dataStore.findOrderId(KEY);

            // Then
            assertThat(result).contains(ORDER_ID);
        }

        @Test
        @DisplayName("should return empty when key does not exist")
        void shouldReturnEmptyWhenKeyDoesNotExist() {
            // Given
            when(repository.findByKey(KEY)).thenReturn(Optional.empty());

            // When
            var result = dataStore.findOrderId(KEY);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should not call save when finding by key")
        void shouldNotCallSaveWhenFindingByKey() {
            // Given
            when(repository.findByKey(KEY)).thenReturn(Optional.empty());

            // When
            dataStore.findOrderId(KEY);

            // Then
            verify(repository, never()).save(any());
        }
    }
}
