package xyz.catuns.edupulse.engagement.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.catuns.edupulse.common.messaging.events.engagement.EngagementScore;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswer;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswerKey;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEvent;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEventKey;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;
import xyz.catuns.edupulse.engagement.domain.serde.StudentEngagementStateSerde;

import java.util.Collections;
import java.util.Map;

@Configuration
public class SerdeConfig {

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Bean
    SpecificAvroSerde<QuizAnswer> quizAnswerSpecificAvroSerde() {
        return getSpecificAvroSerde(specificAvroSerdeConfig(), false);
    }

    @Bean
    SpecificAvroSerde<QuizAnswerKey> quizAnswerKeySpecificAvroSerde() {
        return getSpecificAvroSerde(specificAvroSerdeConfig(), true);
    }

    @Bean
    SpecificAvroSerde<SessionEvent> sessionEventSpecificAvroSerde() {
        return getSpecificAvroSerde(specificAvroSerdeConfig(), false);
    }

    @Bean
    SpecificAvroSerde<SessionEventKey> sessionEventKeySpecificAvroSerde() {
        return getSpecificAvroSerde(specificAvroSerdeConfig(), true);
    }

    @Bean
    SpecificAvroSerde<EngagementScore> engagementScoreSpecificAvroSerde() {
        return getSpecificAvroSerde(specificAvroSerdeConfig(), false);
    }

    @Bean
    public Serde<StudentEngagementState> studentEngagementStateSerde() {
        return new StudentEngagementStateSerde();
    }


    private static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(Map<String, Object> serdeConfig, boolean isKey) {
        SpecificAvroSerde<T> serde = new SpecificAvroSerde<>();
        serde.configure(serdeConfig, isKey);
        return serde;
    }

    private Map<String, Object> specificAvroSerdeConfig() {
        return Collections.singletonMap(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl
        );
    }
}
