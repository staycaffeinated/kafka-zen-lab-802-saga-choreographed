package zen.labs.shipping.adapter.outbound.messaging.serdes;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class JacksonSerializer<T> implements Serializer<T> {

    private final JsonMapper jsonMapper;

    public JacksonSerializer(@Nonnull JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "The JsonMapper must not be null");
    }

    @Override
    @Nullable
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException(String.format("Error serializing data for topic '%s'", topic), e);
        }
    }
}
