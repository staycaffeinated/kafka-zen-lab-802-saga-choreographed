/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.messaging.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("JacksonSerializer")
class JacksonSerializerTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    // ---------------------------------------------------------------------------------
    // Happy-path tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("serialize")
    class Serialize {

        @Test
        @DisplayName("should serialize a valid object to non-null, non-empty bytes")
        void shouldSerializeValidObjectToBytes() {
            // Given
            TestPayload payload = new TestPayload("hello", "world");

            try (JacksonSerializer<TestPayload> serializer = new JacksonSerializer<>(jsonMapper)) {
                // When
                byte[] result = serializer.serialize("test-topic", payload);

                // Then
                assertThat(result).isNotNull().hasSizeGreaterThan(0);
            }
        }

        @Test
        @DisplayName("should produce bytes that deserialize back to the original object")
        void shouldProduceBytesDeserializableToOriginalObject() {
            // Given
            TestPayload original = new TestPayload("foo", "bar");

            try (JacksonSerializer<TestPayload> serializer = new JacksonSerializer<>(jsonMapper)) {
                // When
                byte[] bytes = serializer.serialize("test-topic", original);
                TestPayload restored = jsonMapper.readValue(bytes, TestPayload.class);

                // Then
                assertThat(restored.name()).isEqualTo(original.name());
                assertThat(restored.value()).isEqualTo(original.value());
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Edge-case tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should return null when data is null")
        void shouldReturnNull_whenDataIsNull() {
            try (JacksonSerializer<TestPayload> serializer = new JacksonSerializer<>(jsonMapper)) {
                // When
                byte[] result = serializer.serialize("test-topic", null);

                // Then
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("should throw SerializationException when object has a circular reference")
        void shouldThrowSerializationException_whenObjectHasCircularReference() {
            try (JacksonSerializer<CircularObject> serializer = new JacksonSerializer<>(jsonMapper)) {
                // Given — circular reference causes Jackson infinite recursion
                CircularObject circular = new CircularObject();
                circular.self = circular;

                // When / Then
                assertThatThrownBy(() -> serializer.serialize("test-topic", circular))
                        .isInstanceOf(SerializationException.class);
            }
        }

        @Test
        @DisplayName("should throw NullPointerException when JsonMapper is null")
        void shouldThrowNullPointerException_whenJsonMapperIsNull() {
            // When / Then
            assertThatNullPointerException().isThrownBy(() -> new JacksonSerializer<>(null));
        }
    }

    // ---------------------------------------------------------------------------------
    // Helper types
    // ---------------------------------------------------------------------------------

    record TestPayload(String name, String value) {}

    static class CircularObject {
        public CircularObject self;
    }
}
