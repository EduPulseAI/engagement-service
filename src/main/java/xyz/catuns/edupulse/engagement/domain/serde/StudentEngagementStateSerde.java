package xyz.catuns.edupulse.engagement.domain.serde;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;

import java.util.HashMap;
import java.util.Map;

public class StudentEngagementStateSerde implements Serde<StudentEngagementState> {

    @Override
    public Serializer<StudentEngagementState> serializer() {
        return new JsonSerializer<>();
    }

    @Override
    public Deserializer<StudentEngagementState> deserializer() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StudentEngagementState.class.getName());
        configs.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);  // Ignore missing headers
        configs.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        JsonDeserializer<StudentEngagementState> deserializer = new JsonDeserializer<>(StudentEngagementState.class);
        deserializer.configure(configs, false);  // false for value
        return deserializer;
    }
}
