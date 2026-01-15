# Engagement Scoring Service

A real-time student engagement monitoring system built with **Spring Boot** and **Kafka Streams** that analyzes quiz
answers and session activity to compute engagement scores.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Data Flow](#data-flow)
- [Engagement Score Calculation](#engagement-score-calculation)
- [Schema Design](#schema-design)
- [Stream Topology](#stream-topology)
- [Windowing Strategy](#windowing-strategy)
- [Configuration](#configuration)
- [Building and Running](#building-and-running)
- [Monitoring](#monitoring)
- [Testing Strategy](#testing-strategy)

---

## 🎯 Overview

The Engagement Scoring Service is a **stateful stream processing application** that:

1. **Consumes** from two Kafka topics:
    - `quiz.answers` - Student quiz answer submissions
    - `session.events` - Student session activity (navigation, dwell time, pauses)

2. **Processes** events in **60-second tumbling windows** to:
    - Calculate correctness rates
    - Analyze time spent patterns
    - Detect behavioral patterns (e.g., rapid incorrect submissions)
    - Compute composite engagement scores

3. **Produces** to:
    - `engagement.scores` - Real-time engagement metrics with alert flags

### Key Features

- ✅ **Real-time processing** with sub-second latency
- ✅ **Stateful windowed aggregations** for temporal pattern detection
- ✅ **Avro schema evolution** support via Schema Registry
- ✅ **CloudEvents-compatible** envelope for observability
- ✅ **Alert thresholds** for intervention triggering (score < 0.4)
- ✅ **Spring Boot integration** with health checks and metrics

---

## 🏗️ Architecture

```
┌─────────────────┐
│  Quiz Service   │
└────────┬────────┘
         │
         ├──> quiz.answers
         │    (QuizAnswer events)
         │
         v
    ┌────────────────────────────────┐
    │                                │
    │  Engagement Scoring Service    │
    │  (Kafka Streams Application)   │
    │                                │
    │  ┌──────────────────────────┐  │
    │  │  Stream Topology         │  │
    │  │  ┌────────────────────┐  │  │
    │  │  │ 1. Join Streams    │  │  │
    │  │  │    (by studentId)  │  │  │
    │  │  └─────────┬──────────┘  │  │
    │  │            │              │  │
    │  │  ┌─────────v──────────┐  │  │
    │  │  │ 2. Group by Key    │  │  │
    │  │  │    (studentId)     │  │  │
    │  │  └─────────┬──────────┘  │  │
    │  │            │              │  │
    │  │  ┌─────────v──────────┐  │  │
    │  │  │ 3. Window (60s)    │  │  │
    │  │  │    Tumbling        │  │  │
    │  │  └─────────┬──────────┘  │  │
    │  │            │              │  │
    │  │  ┌─────────v──────────┐  │  │
    │  │  │ 4. Aggregate       │  │  │
    │  │  │    - Correctness   │  │  │
    │  │  │    - Time patterns │  │  │
    │  │  │    - Compute score │  │  │
    │  │  └─────────┬──────────┘  │  │
    │  │            │              │  │
    │  │  ┌─────────v──────────┐  │  │
    │  │  │ 5. Pattern Detect  │  │  │
    │  │  │    - Rapid fails   │  │  │
    │  │  │    - Struggling    │  │  │
    │  │  └─────────┬──────────┘  │  │
    │  │            │              │  │
    │  │  ┌─────────v──────────┐  │  │
    │  │  │ 6. Map to Output   │  │  │
    │  │  │    EngagementScore │  │  │
    │  │  └────────────────────┘  │  │
    │  └──────────────────────────┘  │
    │                                │
    └────────────┬───────────────────┘
                 │
                 v
         engagement.scores
         (EngagementScore events)
                 │
                 v
    ┌────────────┴────────────┐
    │  Downstream Consumers   │
    │  - Alert Service        │
    │  - Analytics Dashboard  │
    │  - Intervention Engine  │
    └─────────────────────────┘
```

### Session Service Integration

```
┌──────────────────┐
│  Session Service │
└────────┬─────────┘
         │
         └──> session.events
              (SessionEvent: NAVIGATION, DWELL, PAUSED, etc.)
```

---

## 🔄 Data Flow

### Input Streams

#### 1. `quiz.answers` Topic

- **Key**: `QuizAnswerKey` (studentId, questionId)
- **Value**: `QuizAnswer`
- **Purpose**: Capture answer submissions with correctness and timing

#### 2. `session.events` Topic

- **Key**: `SessionEventKey` (sessionId)
- **Value**: `SessionEvent`
- **Purpose**: Track session activity patterns

### Output Stream

#### `engagement.scores` Topic

- **Key**: `studentId` (String)
- **Value**: `EngagementScore`
- **Purpose**: Real-time engagement metrics with intervention triggers

---

## 🧮 Engagement Score Calculation

The engagement score is a **composite metric** (0.0 to 1.0) computed from multiple signals:

### Formula

```
engagementScore = (dwellScore × 0.30) + 
                  (accuracyScore × 0.40) + 
                  (pacingScore × 0.30)
```

### Component Scores

#### 1. **Accuracy Score** (40% weight)

```
correctnessRate = correctAnswers / totalAnswers

accuracyScore = correctnessRate
```

**Example**:

- 1 correct out of 3 recent answers → 0.33

#### 2. **Dwell Score** (30% weight)

```
avgTimeSpent = sum(timeSpentMs) / count(answers)

If avgTimeSpent > STRUGGLING_THRESHOLD (15000ms):
    dwellScore = 0.3  // Spending too long (struggling)
Else if avgTimeSpent < RUSHING_THRESHOLD (5000ms):
    dwellScore = 0.5  // Rushing through
Else:
    dwellScore = 1.0  // Healthy pace
```

**Example**:

- Average 18,000ms per question → 0.3 (struggling)

#### 3. **Pacing Score** (30% weight)

```
questionsPerMinute = count(answers) / (windowSizeSeconds / 60)

If questionsPerMinute > IDEAL_PACE (±20%):
    pacingScore = 0.7
Else:
    pacingScore = 1.0
```

### Pattern Detection

Additional flags for qualitative insights:

```java
if(incorrectCount >=3&&avgTimeSpent< 10000ms){
pattern ="rapid_incorrect_submissions"
        // Student may be guessing or disengaged
        }

        if(avgTimeSpent >20000ms &&correctnessRate< 0.5){
pattern ="struggling_extensively"
        // Student needs intervention
        }
```

### Alert Thresholds

```
engagementScore < 0.4  → alertThresholdCrossed = true (RED ALERT)
engagementScore < 0.6  → trend = DECLINING (YELLOW WARNING)
engagementScore >= 0.6 → trend = STABLE or RISING
```

### Example Calculation

**Scenario**: Alice's recent activity (60-second window)

- 3 quiz answers: 1 correct, 2 incorrect
- Average time spent: 18,000ms
- Questions per minute: 3

```
accuracyScore  = 1/3 = 0.33
dwellScore     = 0.3  (struggling - above 15s threshold)
pacingScore    = 1.0  (normal pace)

engagementScore = (0.3 × 0.30) + (0.33 × 0.40) + (1.0 × 0.30)
                = 0.09 + 0.132 + 0.30
                = 0.522

However, with pattern detection:
- Rapid incorrect submissions detected
- Adjusts score downward → 0.38

Result: alertThresholdCrossed = true, trend = CRITICAL
```

---

## 📊 Schema Design

All events use **Avro schemas** registered in Confluent Schema Registry.

### EventEnvelope (Common)

**CloudEvents-compatible** metadata wrapper:

```
EventEnvelope {
  id: String              // UUID v4
  source: String          // e.g., "quiz-service"
  type: String            // e.g., "quiz.answered"
  specversion: String     // "1.0"
  timestamp: Long         // Epoch millis (UTC)
  studentId: String       // Anonymized ID
  sessionId: String       // Session correlation
  correlationId: String?  // Cross-service tracing
}
```

### Quiz Answer Schema

```
QuizAnswerKey {
  studentId: String
  questionId: String
}

QuizAnswer {
  envelope: EventEnvelope
  questionId: String
  skillTag: String              // "algebra.linear-equations"
  difficultyLevel: Int          // 1-5
  answer: String
  isCorrect: Boolean
  attemptNumber: Int
  timeSpentMs: Long
  contextualData: QuizContext {
    hintsUsed: Int
    previousAnswers: Array<String>
  }
}
```

### Session Event Schema

```
SessionEventKey {
  sessionId: String
}

SessionEvent {
  envelope: EventEnvelope
  eventType: Enum {
    STARTED, NAVIGATION, DWELL, 
    PAUSED, RESUMED, COMPLETED
  }
  pageId: String?
  dwellTimeMs: Long?
  metadata: Map<String, String>
}
```

### Engagement Score Schema (Output)

```
EngagementScore {
  envelope: EventEnvelope
  score: Double                    // 0.0 - 1.0
  scoreComponents: {
    dwellScore: Double             // 0.0 - 1.0
    accuracyScore: Double          // 0.0 - 1.0
    pacingScore: Double            // 0.0 - 1.0
    attentionScore: Double?        // Optional webcam tracking
  }
  trend: Enum {
    RISING, STABLE, DECLINING, CRITICAL
  }
  alertThresholdCrossed: Boolean   // true if score < 0.4
}
```

---

## 🔀 Stream Topology

### High-Level Topology

```java
KStream<QuizAnswerKey, QuizAnswer> quizStream = builder.stream("quiz.answers");
KStream<SessionEventKey, SessionEvent> sessionStream = builder.stream("session.events");

// 1. Re-key both streams by studentId for joining
KStream<String, QuizAnswer> quizByStudent = quizStream
        .selectKey((key, value) -> value.getEnvelope().getStudentId());

KStream<String, SessionEvent> sessionByStudent = sessionStream
        .selectKey((key, value) -> value.getEnvelope().getStudentId());

// 2. Merge streams (we care about all events for a student)
KStream<String, EnrichedEvent> mergedStream = quizByStudent
        .merge(sessionByStudent.mapValues(this::convertToEnrichedEvent));

// 3. Group by studentId
KGroupedStream<String, EnrichedEvent> grouped = mergedStream
        .groupByKey();

// 4. Apply 60-second tumbling window
TimeWindows window = TimeWindows
        .ofSizeWithNoGrace(Duration.ofSeconds(60));

KTable<Windowed<String>, EngagementAggregate> windowed = grouped
        .windowedBy(window)
        .aggregate(
                EngagementAggregate::new,        // Initializer
                this::aggregateEngagementData,   // Aggregator
                Materialized.with(stringSerde, aggregateSerde)
        );

// 5. Compute engagement score and detect patterns
KStream<String, EngagementScore> scores = windowed
        .toStream()
        .mapValues(this::computeEngagementScore)
        .selectKey((windowedKey, value) -> windowedKey.key());

// 6. Produce to output topic
scores.

to("engagement.scores");
```

### Detailed Processing Steps

#### Step 1: Stream Ingestion

```java
// Consume with Avro deserialization
StreamsBuilder builder = new StreamsBuilder();

KStream<QuizAnswerKey, QuizAnswer> quizAnswers = builder.stream(
        "quiz.answers",
        Consumed.with(quizAnswerKeySerde, quizAnswerSerde)
);

KStream<SessionEventKey, SessionEvent> sessionEvents = builder.stream(
        "session.events",
        Consumed.with(sessionEventKeySerde, sessionEventSerde)
);
```

#### Step 2: Re-keying

```java
// Both streams need same key (studentId) for co-partitioning
KStream<String, QuizAnswer> quizByStudent = quizAnswers
                .selectKey((key, quiz) -> quiz.getEnvelope().getStudentId());

KStream<String, SessionEvent> sessionByStudent = sessionEvents
        .selectKey((key, session) -> session.getEnvelope().getStudentId());
```

#### Step 3: Windowing

```java
// 60-second tumbling window (non-overlapping)
TimeWindows tumblingWindow = TimeWindows
                .ofSizeWithNoGrace(Duration.ofSeconds(60));

// Windows: [0-60s], [60-120s], [120-180s], ...
```

#### Step 4: Aggregation

```java
public EngagementAggregate aggregate(
        String key,
        QuizAnswer quiz,
        EngagementAggregate agg
) {
    agg.addQuizAnswer(quiz);

    // Update metrics
    if (quiz.getIsCorrect()) {
        agg.incrementCorrect();
    } else {
        agg.incrementIncorrect();
    }

    agg.addTimeSpent(quiz.getTimeSpentMs());
    agg.updateTimestamp(quiz.getEnvelope().getTimestamp());

    return agg;
}
```

#### Step 5: Score Calculation

```java
public EngagementScore computeEngagementScore(EngagementAggregate agg) {
    double correctnessRate = agg.getCorrectCount() /
            (double) agg.getTotalCount();

    double avgTimeSpent = agg.getTotalTimeSpent() /
            (double) agg.getTotalCount();

    // Compute component scores
    double accuracyScore = correctnessRate;
    double dwellScore = computeDwellScore(avgTimeSpent);
    double pacingScore = computePacingScore(agg.getTotalCount());

    // Weighted composite
    double finalScore = (accuracyScore * 0.4) +
            (dwellScore * 0.3) +
            (pacingScore * 0.3);

    // Pattern detection adjustments
    String pattern = detectPattern(agg);
    if ("rapid_incorrect_submissions".equals(pattern)) {
        finalScore *= 0.8; // 20% penalty
    }

    return buildEngagementScore(finalScore, agg);
}
```

---

## ⏰ Windowing Strategy

### Tumbling Windows (60 seconds)

**Characteristics**:

- Fixed-size, non-overlapping
- Each event belongs to exactly one window
- Window closes after 60 seconds of event time

**Window Assignment**:

```
Event Time    Window
---------     ------
10:00:05  →   [10:00:00 - 10:01:00)
10:00:45  →   [10:00:00 - 10:01:00)
10:01:15  →   [10:01:00 - 10:02:00)
10:01:59  →   [10:01:00 - 10:02:00)
```

**Why 60 seconds?**

- ✅ Captures short-term behavioral patterns
- ✅ Responsive enough for intervention (1-minute latency)
- ✅ Reduces noise from single anomalous events
- ✅ Aligns with typical quiz-taking cadence (3-5 questions/min)

### Grace Period

```java
// No grace period (strict window boundaries)
TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60))
```

**Trade-off**: Late-arriving events (>60s delay) are dropped for deterministic processing.

### Alternative Windowing (Future Enhancement)

**Hopping Windows** (60s size, 30s advance):

```java
TimeWindows.ofSizeAndGrace(
        Duration.ofSeconds(60),
    Duration.

ofSeconds(30)
)
```

- Provides overlapping views (smoother trend detection)
- Each event appears in 2 windows
- Higher computational cost

---

## ⚙️ Configuration

### Application Properties

```yaml
spring:
  application:
    name: engagement-scoring-service

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

    streams:
      application-id: engagement-scoring-v1
      replication-factor: 3
      num-stream-threads: 4

      properties:
        # Processing guarantees
        processing.guarantee: exactly_once_v2

        # State store
        state.dir: /tmp/kafka-streams

        # Commit interval
        commit.interval.ms: 1000

        # Consumer configs
        auto.offset.reset: earliest
        max.poll.records: 1000

        # Producer configs
        compression.type: snappy
        linger.ms: 100
        batch.size: 32768

# Schema Registry
schema:
  registry:
    url: ${SCHEMA_REGISTRY_URL:http://localhost:8081}

# Engagement thresholds
engagement:
  scoring:
    alert-threshold: 0.4
    warning-threshold: 0.6
    struggling-time-threshold-ms: 15000
    rushing-time-threshold-ms: 5000
    ideal-questions-per-minute: 3

# Monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Environment Variables

```bash
# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
SCHEMA_REGISTRY_URL=http://schema-registry:8081

# Application
SPRING_PROFILES_ACTIVE=production
LOG_LEVEL=INFO

# JVM (recommended for production)
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
```

---

## 🚀 Building and Running

### Prerequisites

- Java 17+
- Maven 3.8+
- Kafka 3.6+
- Confluent Schema Registry 7.5+

### Build

```bash
# Generate Avro classes from schemas
mvn clean generate-sources

# Compile and package
mvn clean package

# Run tests
mvn test
```

### Run Locally

```bash
# 1. Start Kafka and Schema Registry (Docker)
docker-compose up -d

# 2. Create topics
kafka-topics --create --topic quiz.answers --partitions 6 --replication-factor 1
kafka-topics --create --topic session.events --partitions 6 --replication-factor 1
kafka-topics --create --topic engagement.scores --partitions 6 --replication-factor 1

# 3. Run application
mvn spring-boot:run

# Or with JAR
java -jar target/engagement-scoring-service-1.0.0-SNAPSHOT.jar
```

### Docker Deployment

```bash
# Build image
docker build -t edupulse/engagement-scoring:latest .

# Run container
docker run -d \
  --name engagement-scoring \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SCHEMA_REGISTRY_URL=http://schema-registry:8081 \
  -p 8080:8080 \
  edupulse/engagement-scoring:latest
```

---

## 📈 Monitoring

### Health Checks

```bash
# Liveness
curl http://localhost:8080/actuator/health/liveness

# Readiness
curl http://localhost:8080/actuator/health/readiness
```

### Metrics (Prometheus)

```bash
# Scrape endpoint
curl http://localhost:8080/actuator/prometheus
```

**Key Metrics**:

```
# Stream processing
kafka_streams_stream_task_commit_latency_avg
kafka_streams_stream_thread_process_latency_avg

# Custom metrics
engagement_scores_produced_total
engagement_alerts_triggered_total
engagement_score_histogram

# State store
kafka_streams_state_store_get_latency_avg
```

---

## 🔍 Troubleshooting

### Common Issues

**1. State Store Corruption**

```bash
# Reset state
kafka-streams-application-reset --application-id engagement-scoring-v1 \
  --bootstrap-servers localhost:9092
```

**2. Schema Registry Connection**

```bash
# Verify connectivity
curl http://schema-registry:8081/subjects

# Check schema versions
curl http://schema-registry:8081/subjects/engagement.scores-value/versions
```

**3. Lag Monitoring**

```bash
# Check consumer group lag
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group engagement-scoring-v1 \
  --describe
```

---

## 📚 References

- [Kafka Streams Documentation](https://kafka.apache.org/documentation/streams/)
- [Spring Kafka Streams](https://spring.io/projects/spring-kafka)
- [Avro Schema Evolution](https://docs.confluent.io/platform/current/schema-registry/avro.html)
- [CloudEvents Specification](https://cloudevents.io/)

---

## 📄 License

Copyright © 2026 EduPulse. All rights reserved.