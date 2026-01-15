package xyz.catuns.edupulse.engagement.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.TopicBuilder;
import xyz.catuns.edupulse.engagement.config.properties.AppProperties;
import xyz.catuns.spring.base.properties.KafkaTopicProperties;

import java.util.Map;

@Configuration
@EnableKafkaStreams
class KafkaConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${spring.kafka.streams.application-id}")
	private String streamsAppId;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration defaultKafkaStreamsConfig(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildStreamsProperties((SslBundles) null);
        return new KafkaStreamsConfiguration(props);
    }

    @Value("${app.kafka.topics.engagement}")
    private String engagementTopicName;

    @Bean
    NewTopic engagementTopic(AppProperties appProperties) {
        KafkaTopicProperties topicProperties = appProperties.getKafka();
        return TopicBuilder.name(engagementTopicName)
                .replicas(topicProperties.getReplicas())
                .partitions(topicProperties.getPartitions())
                .build();
    }
}
