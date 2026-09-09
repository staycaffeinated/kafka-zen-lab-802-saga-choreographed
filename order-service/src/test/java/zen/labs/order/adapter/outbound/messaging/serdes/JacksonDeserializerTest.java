/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.outbound.messaging.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("JacksonDeserializer")
class JacksonDeserializerTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    // ---------------------------------------------------------------------------------
    // Happy-path tests
    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("deserialize")
    class Deserialize {

        @Test
        @DisplayName("should deserialize valid bytes to the expected object")
        void shouldDeserializeValidBytes_toExpectedObject() {
            // Given
            TestPayload original = new TestPayload("alpha", "beta");
            byte[] bytes = jsonMapper.writeValueAsBytes(original);

            try (JacksonDeserializer<TestPayload> deserializer =
                    new JacksonDeserializer<>(TestPayload.class, jsonMapper)) {
                // When
                TestPayload result = deserializer.deserialize("test-topic", bytes);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.name()).isEqualTo(original.name());
                assertThat(result.value()).isEqualTo(original.value());
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
            try (JacksonDeserializer<TestPayload> deserializer =
                    new JacksonDeserializer<>(TestPayload.class, jsonMapper)) {
                // When
                TestPayload result = deserializer.deserialize("test-topic", null);

                // Then
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("should return null when bytes contain malformed JSON")
        void shouldReturnNull_whenBytesContainMalformedJson() {
            // Given — malformed JSON cannot be parsed; the implementation logs and returns null
            byte[] malformed = "not-valid-json{{{{".getBytes();

            try (JacksonDeserializer<TestPayload> deserializer =
                    new JacksonDeserializer<>(TestPayload.class, jsonMapper)) {
                // When
                TestPayload result = deserializer.deserialize("test-topic", malformed);

                // Then
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("should return object with null fields when JSON has no matching properties")
        void shouldReturnObjectWithNullFields_whenJsonHasNoMatchingProperties() {
            // Given — structurally valid JSON but none of the fields match TestPayload
            byte[] wrongFields = "{\"foo\":\"bar\",\"baz\":42}".getBytes();

            try (JacksonDeserializer<TestPayload> deserializer =
                    new JacksonDeserializer<>(TestPayload.class, jsonMapper)) {
                // When
                TestPayload result = deserializer.deserialize("test-topic", wrongFields);

                // Then — Jackson creates an instance but all fields remain null
                assertThat(result).isNotNull();
                assertThat(result.name()).isNull();
                assertThat(result.value()).isNull();
            }
        }

        @Test
        @DisplayName("should throw NullPointerException when targetType is null")
        void shouldThrowNullPointerException_whenTargetTypeIsNull() {
            // When / Then
            assertThatNullPointerException().isThrownBy(() -> new JacksonDeserializer<>(null, jsonMapper));
        }

        @Test
        @DisplayName("should throw NullPointerException when JsonMapper is null")
        void shouldThrowNullPointerException_whenJsonMapperIsNull() {
            // When / Then
            assertThatNullPointerException().isThrownBy(() -> new JacksonDeserializer<>(TestPayload.class, null));
        }
    }

    // ---------------------------------------------------------------------------------
    // Helper types
    // ---------------------------------------------------------------------------------

    record TestPayload(String name, String value) {}
}
