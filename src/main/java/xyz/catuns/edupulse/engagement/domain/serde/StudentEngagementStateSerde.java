package xyz.catuns.edupulse.engagement.domain.serde;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;

public class StudentEngagementStateSerde implements Serde<StudentEngagementState> {

    @Override
    public Serializer<StudentEngagementState> serializer() {
        return new JsonSerializer<>();
    }

    @Override
    public Deserializer<StudentEngagementState> deserializer() {
        return new JsonDeserializer<>();
    }
}
