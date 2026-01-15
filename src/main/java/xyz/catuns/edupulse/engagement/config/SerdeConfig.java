package xyz.catuns.edupulse.engagement.config;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serde;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.catuns.edupulse.common.messaging.events.engagement.EngagementScore;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswer;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswerKey;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEvent;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEventKey;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;
import xyz.catuns.edupulse.engagement.domain.serde.StudentEngagementStateSerde;

import java.util.Map;

@Configuration
public class SerdeConfig {

    private final Map<String, String> schemaConfigProps;

    public SerdeConfig(KafkaProperties kafkaProperties) {
        this.schemaConfigProps = kafkaProperties.getProperties();
    }

    @Bean
    SpecificAvroSerde<QuizAnswer> quizAnswerSpecificAvroSerde() {
        return getSpecificAvroSerde(schemaConfigProps, false);
    }

    @Bean
    SpecificAvroSerde<QuizAnswerKey> quizAnswerKeySpecificAvroSerde() {
        return getSpecificAvroSerde(schemaConfigProps, true);
    }

    @Bean
    SpecificAvroSerde<SessionEvent> sessionEventSpecificAvroSerde() {
        return getSpecificAvroSerde(schemaConfigProps, false);
    }

    @Bean
    SpecificAvroSerde<SessionEventKey> sessionEventKeySpecificAvroSerde() {
        return getSpecificAvroSerde(schemaConfigProps, true);
    }

    @Bean
    SpecificAvroSerde<EngagementScore> engagementScoreSpecificAvroSerde() {
        return getSpecificAvroSerde(schemaConfigProps, false);
    }

    @Bean
    public Serde<StudentEngagementState> studentEngagementStateSerde() {
        return new StudentEngagementStateSerde();
    }


    private static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(Map<String, String> serdeConfig, boolean isKey) {
        SpecificAvroSerde<T> serde = new SpecificAvroSerde<>();
        serde.configure(serdeConfig, isKey);
        return serde;
    }

}
