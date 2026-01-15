package xyz.catuns.edupulse.engagement.topology;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.catuns.edupulse.common.messaging.events.engagement.EngagementScore;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswer;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswerKey;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEvent;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEventKey;
import xyz.catuns.edupulse.engagement.config.properties.ScoringProperties;
import xyz.catuns.edupulse.engagement.domain.events.EnrichedEvent;
import xyz.catuns.edupulse.engagement.domain.mapper.EnrichedEventMapper;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;
import xyz.catuns.edupulse.engagement.service.EngagementScoringService;
import xyz.catuns.edupulse.engagement.service.aggregate.StudentEngagementAggregator;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class EngagementScoringTopology {

    private final ScoringProperties scoringProperties;
    private final StudentEngagementAggregator aggregator;
    private final EngagementScoringService scoringService;

    // Mappers
    private final EnrichedEventMapper enrichedEventMapper;

    // Serdes
    private final SpecificAvroSerde<QuizAnswer> quizAnswerSerde;
    private final SpecificAvroSerde<QuizAnswerKey> quizAnswerKeySerde;
    private final SpecificAvroSerde<SessionEvent> sessionEventSerde;
    private final SpecificAvroSerde<SessionEventKey> sessionEventKeySerde;
    private final SpecificAvroSerde<EngagementScore> engagementScoreSerde;
    private final Serde<StudentEngagementState> stateSerde;

    // Topics
    @Value("${app.kafka.topics.answer}")
    private String quizAnswersTopic;

    @Value("${app.kafka.topics.session}")
    private String sessionEventsTopic;

    @Value("${app.kafka.topics.engagement}")
    private String engagementScoresTopic;


    /**
     * Build Kafka Streams topology
     */
    @Autowired
    public void buildTopology(StreamsBuilder builder) {
        log.info("Building Engagement Scoring topology...");

        KStream<String, QuizAnswer> quizAnswerStream = quizAnswerByStudentKStream(builder);
        KStream<String, SessionEvent> sessionEventStream = sessionEventByStudentKStream(builder);

        // 1. Merge both streams into a unified stream
        KStream<String, EnrichedEvent> quizEnrichedStream = quizAnswerStream
                .map((studentId, quizAnswer) -> KeyValue.pair(
                        studentId,
                        enrichedEventMapper.fromQuizAnswer(quizAnswer)
                ));

        KStream<String, EnrichedEvent> sessionEnrichedStream = sessionEventStream
                .map((studentId, sessionEvent) -> KeyValue.pair(
                        studentId,
                        enrichedEventMapper.fromSessionEvent(sessionEvent)
                ));

        KStream<String, EnrichedEvent> mergedStream = quizEnrichedStream.merge(sessionEnrichedStream);

        // 2. Group by studentId and window (tumbling/hopping)
        KGroupedStream<String, EnrichedEvent> groupedStream = mergedStream
                .groupByKey(Grouped.with(Serdes.String(), null));

        TimeWindows timeWindows = TimeWindows.ofSizeAndGrace(
                Duration.ofSeconds(scoringProperties.getWindow().getDurationSeconds()),
                Duration.ofSeconds(scoringProperties.getWindow().getGracePeriodSeconds())
        );

        // 3. Aggregate events
        KTable<Windowed<String>, StudentEngagementState> aggregatedState = groupedStream
                .windowedBy(timeWindows)
                .aggregate(
                        // Initializer
                        StudentEngagementState::new,
                        // Aggregator
                        aggregator::aggregate,
                        // Materialized view configuration
                        Materialized.<String, StudentEngagementState>as(
                                        Stores.persistentWindowStore(
                                                "engagement-aggregate-store",
                                                Duration.ofSeconds(scoringProperties.getWindow().getDurationSeconds()),
                                                Duration.ofSeconds(scoringProperties.getWindow().getDurationSeconds()),
                                                false
                                        ))
                                .withKeySerde(Serdes.String())
                                .withValueSerde(stateSerde)
                                .withCachingEnabled()
                );

        // 6. Compute engagement scores
        KStream<Windowed<String>, EngagementScore> engagementScoreStream = aggregatedState
                .toStream()
                .peek((windowedKey, aggregate) -> {
                    log.debug("Computing score for window: student={}, window=[{}-{}], totalAnswers={}, correctness={}",
                            windowedKey.key(),
                            windowedKey.window().start(),
                            windowedKey.window().end(),
                            aggregate.getTotalAnswers(),
                            aggregate.getCorrectnessRate());
                })
                .mapValues(this::computeEngagementScore, Named.as("compute-engagement-score"));

        // Rekey to student id
        KStream<String, EngagementScore> engagementScores = engagementScoreStream
                .selectKey(
                        (windowedKey, score) -> windowedKey.key(),
                        Named.as("rekey-by-student")
                )
                .filter(
                        (key, score) -> score != null,
                        Named.as("filter-null-scores")
                )
                .peek((studentId, score) -> log.info(
                        "Computed engagement score: studentId={}, score={}, trend={}, alert={}",
                        studentId,
                        score.getScore(),
                        score.getTrend(),
                        score.getAlertThresholdCrossed()
                ));

        // 7. Produce to output topic
        engagementScores.to(
                engagementScoresTopic,
                Produced.with(Serdes.String(), engagementScoreSerde)
                        .withName("engagement-scores-sink")
        );

        log.info("Engagement Scoring topology built successfully");
    }

    private EngagementScore computeEngagementScore(Windowed<String> windowedKey, StudentEngagementState aggregate) {
        // Compute engagement score
        EngagementScore score = scoringService.calculate(aggregate);

        // Record metrics
//                    engagementScoreDistribution.record(score.getScore());

        if (score.getAlertThresholdCrossed()) {
//                        alertsTriggeredCounter.increment();
            log.warn("ALERT: Low engagement detected for student={}, score={}, trend={}",
                    windowedKey.key(), score.getScore(), score.getTrend());
        }

        // Detect and log patterns
//                    BehavioralPattern pattern = detectBehavioralPattern(aggregate);
//                    if (pattern != BehavioralPattern.NORMAL) {
//                        patternsDetectedCounter.increment();
//                        log.info("Pattern detected: student={}, pattern={}",
//                                windowedKey.key(), pattern);
//                    }

        return score;
    }

    private KStream<String, QuizAnswer> quizAnswerByStudentKStream(StreamsBuilder builder) {

        return builder.stream(
                        quizAnswersTopic,
                        Consumed.with(quizAnswerKeySerde, quizAnswerSerde)
                                .withName("quiz-answers-source")
                                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST)
                )
                .selectKey(
                        (key, value) -> value.getEnvelope().getStudentId(),
                        Named.as("rekey-quiz-answers-by-student")
                )
                .peek((key, value) -> log.debug(
                        "Consumed quiz answer: studentId={}, questionId={}, correct={}",
                        key,
                        value.getQuestionId(),
                        value.getIsCorrect()
                ));
    }

    private KStream<String, SessionEvent> sessionEventByStudentKStream(StreamsBuilder builder) {
        return builder.stream(
                        sessionEventsTopic,
                        Consumed.with(sessionEventKeySerde, sessionEventSerde)
                                .withName("session-events-source")
                                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST)
                )
                .selectKey(
                        (key, value) -> value.getEnvelope().getStudentId(),
                        Named.as("rekey-session-events-by-student")
                )
                .peek((key, value) -> log.debug(
                        "Consumed session event: studentId={}, type={}",
                        value.getEnvelope().getStudentId(),
                        value.getEventType()
                ));
    }


}
