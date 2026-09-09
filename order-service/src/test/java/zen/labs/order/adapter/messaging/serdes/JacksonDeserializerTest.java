package zen.labs.order.adapter.messaging.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.order.adapter.outbound.messaging.serdes.JacksonDeserializer;

class JacksonDeserializerTest {

    JsonMapper jsonMapper = new JsonMapper();

    @Test
    void shouldDeserializeValidObject() {
        TestObject expected = new TestObject("widget-title", "widget-author");
        String json = jsonMapper.writeValueAsString(expected);
        byte[] raw = json.getBytes();

        try (JacksonDeserializer<TestObject> deserializer = new JacksonDeserializer<>(TestObject.class, jsonMapper)) {
            deserializer.configure(new HashMap<>(), false);

            TestObject actual = deserializer.deserialize("fake-topic", raw);
            Assertions.assertNotNull(actual);
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void whenDataIsNull_thenReturnsNull() {
            try (JacksonDeserializer<TestObject> deserializer = new JacksonDeserializer<>(TestObject.class)) {
                TestObject actual = deserializer.deserialize("fake-topic", null);
                assertThat(actual).isNull();
            }
        }

        @Test
        void whenBadData_thenReturnsNonNull() {
            try (JacksonDeserializer<TestObject> deserializer = new JacksonDeserializer<>(TestObject.class)) {
                byte[] wrongStructure = "{\"wrongField\":\"value\",\"anotherWrongField\":123}".getBytes();
                TestObject actual = deserializer.deserialize("fake-topic", wrongStructure);
                assertThat(actual).isNotNull(); // an object is returned
                assertThat(actual.title()).isNull(); // but its fields are null
                assertThat(actual.description()).isNull();
            }
        }

        @Test
        void whenMalformedJson_thenReturnsNull() {
            try (JacksonDeserializer<TestObject> deserializer = new JacksonDeserializer<>(TestObject.class)) {
                byte[] malformed = "not-valid-json".getBytes();
                TestObject actual = deserializer.deserialize("fake-topic", malformed);
                assertThat(actual).isNull();
            }
        }

        @ParameterizedTest
        @NullSource
        void throwsExceptionIfTargetClassIsNull(Class<?> targetClass) {
            assertThrows(NullPointerException.class, () -> new JacksonDeserializer<>(targetClass));
        }

        @ParameterizedTest
        @NullSource
        void throwsExceptionIfObjectMapperIsNull(JsonMapper jsonMapper) {
            assertThrows(NullPointerException.class, () -> new JacksonDeserializer<>(String.class, jsonMapper));
        }
    }

    /*
     * Helper classes
     */
    record TestObject(String title, String description) implements java.io.Serializable {}
}
