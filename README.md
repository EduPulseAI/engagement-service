# Engagement Service

Real-time student engagement scoring service using Kafka Streams for EduPulse.

## Overview

The Engagement Service processes quiz answers and session events from Kafka, computes engagement scores using a
windowed aggregation pipeline, and publishes scores to downstream consumers. It provides:

- Kafka Streams topology for real-time event processing
- Windowed aggregation of student activity (60-second tumbling windows)
- Weighted composite engagement scoring (accuracy, dwell time, pacing)
- Trend detection (rising, stable, declining, critical)
- Alert threshold monitoring for low engagement
- Exactly-once processing semantics

## Architecture

```
Kafka Topics                    Engagement Service                Output Topic
┌──────────────────┐           ┌──────────────────────┐          ┌─────────────────┐
│ quiz.answers     │──────────▶│                      │          │                 │
│ (QuizAnswer)     │           │  ┌────────────────┐  │          │ engagement      │
├──────────────────┤           │  │ Merge Streams  │  │          │ .scores         │
│ session.events   │──────────▶│  │ (by studentId) │  │─────────▶│ (EngagementScore│
│ (SessionEvent)   │           │  └───────┬────────┘  │          │                 │
└──────────────────┘           │          │           │          └─────────────────┘
                               │  ┌───────▼────────┐  │
                               │  │ Windowed       │  │
                               │  │ Aggregation    │  │
                               │  │ (60s tumbling) │  │
                               │  └───────┬────────┘  │
                               │          │           │
                               │  ┌───────▼────────┐  │
                               │  │ Scoring        │  │
                               │  │ Service        │  │
                               │  └────────────────┘  │
                               └──────────────────────┘
```

## Scoring Algorithm

### Components

The engagement score is a weighted composite of three factors:

| Component | Weight | Description                                            |
|-----------|--------|--------------------------------------------------------|
| Accuracy  | 40%    | Correctness rate (correct answers / total answers)     |
| Dwell     | 30%    | Time spent per question (penalizes rushing/struggling) |
| Pacing    | 30%    | Questions per minute relative to expected pace         |

### Formula

```
engagementScore = (accuracyScore × 0.40) +
                  (dwellScore × 0.30) +
                  (pacingScore × 0.30)
```

### Thresholds

| Threshold | Value  | Meaning                   |
|-----------|--------|---------------------------|
| Green     | >= 0.7 | Healthy engagement        |
| Yellow    | >= 0.4 | Warning zone              |
| Alert     | < 0.4  | Critical - triggers alert |

### Time Thresholds

| Pattern    | Duration     | Dwell Score |
|------------|--------------|-------------|
| Rushing    | < 5 seconds  | 0.5         |
| Healthy    | 5-15 seconds | 1.0         |
| Struggling | > 15 seconds | 0.3         |

### Trend Detection

- `RISING` - Score >= 0.8 with good accuracy and healthy pace
- `STABLE` - Score in green zone with normal patterns
- `DECLINING` - Score in yellow zone
- `CRITICAL` - Score below alert threshold

## Configuration

### Application Properties

```yaml
app:
  scoring:
    weights:
      accuracy: 0.4
      dwell: 0.3
      pacing: 0.3
    thresholds:
      alert: 0.4
      green: 0.7
      yellow: 0.4
    window:
      duration-seconds: 60
      grace-period-seconds: 5
  kafka:
    topics:
      answer: quiz.answers
      session: session.events
      engagement: engagement.scores
```

### Environment Variables

| Variable                     | Description                 | Required |
|------------------------------|-----------------------------|----------|
| `KAFKA_BOOTSTRAP_SERVERS`    | Kafka bootstrap servers     | Yes      |
| `KAFKA_API_KEY`              | Confluent Cloud API key     | Yes      |
| `KAFKA_API_SECRET`           | Confluent Cloud API secret  | Yes      |
| `SCHEMA_REGISTRY_URL`        | Schema Registry URL         | Yes      |
| `SCHEMA_REGISTRY_API_KEY`    | Schema Registry API key     | Yes      |
| `SCHEMA_REGISTRY_API_SECRET` | Schema Registry API secret  | Yes      |
| `SPRING_PROFILES_ACTIVE`     | Active profile (dev/prod)   | No       |
| `SERVER_PORT`                | Server port (default: 8080) | No       |

### Kafka Streams Configuration

| Property                    | Value                                   | Description            |
|-----------------------------|-----------------------------------------|------------------------|
| `application-id`            | `engagement-service-streams`            | Consumer group ID      |
| `processing.guarantee`      | `exactly_once_v2`                       | Exactly-once semantics |
| `commit.interval.ms`        | `1000`                                  | State commit interval  |
| `cache.max.bytes.buffering` | `10 MB`                                 | Per-thread cache size  |
| `state.dir`                 | `/tmp/kafka-streams/engagement-service` | RocksDB state store    |

## Building

### Maven Build

```bash
./mvnw clean install
```

### Docker Build

```bash
./mvnw spring-boot:build-image
```

Or using the project Makefile from `backend/`:

```bash
make docker-build-engagement-service
make docker-push-engagement-service
```

## Running

### Local Development

```bash
# Start with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or with environment variables
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./mvnw spring-boot:run
```

### Docker

```bash
docker run -p 8082:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e KAFKA_API_KEY=your-key \
  -e KAFKA_API_SECRET=your-secret \
  us-central1-docker.pkg.dev/edupulse-483220/edupulse/engagement-service:latest
```

## Input Events

### QuizAnswer

Consumed from `quiz.answers` topic:

```json
{
  "envelope": {
    "id": "uuid",
    "studentId": "student-123",
    "sessionId": "session-456",
    "timestamp": 1234567890
  },
  "questionId": "q-001",
  "isCorrect": true,
  "timeSpentMs": 8500,
  "hintsUsed": 0
}
```

### SessionEvent

Consumed from `session.events` topic:

```json
{
  "envelope": {
    "id": "uuid",
    "studentId": "student-123",
    "sessionId": "session-456",
    "timestamp": 1234567890
  },
  "eventType": "FOCUS_LOST"
}
```

## Output Event

### EngagementScore

Published to `engagement.scores` topic:

```json
{
  "envelope": {
    "id": "uuid",
    "type": "engagement.scored",
    "studentId": "student-123",
    "sessionId": "session-456",
    "timestamp": 1234567890
  },
  "score": 0.72,
  "scoreComponents": {
    "accuracyScore": 0.8,
    "dwellScore": 1.0,
    "pacingScore": 0.5
  },
  "trend": "STABLE",
  "alertThresholdCrossed": false
}
```

## Stream Topology

The Kafka Streams topology processes events in the following stages:

1. **Consume** - Read from `quiz.answers` and `session.events` topics with Avro deserialization
2. **Re-key** - Re-key both streams by `studentId` for co-partitioning
3. **Merge** - Combine quiz and session streams into unified `EnrichedEvent` stream
4. **Window** - Apply 60-second tumbling windows with 5-second grace period
5. **Aggregate** - Accumulate `StudentEngagementState` per student per window
6. **Score** - Compute weighted engagement score with trend and alert detection
7. **Produce** - Publish `EngagementScore` to output topic

## Metrics

Prometheus metrics are exposed at `/actuator/prometheus`:

- `kafka_streams_state` - Kafka Streams application state
- `kafka_streams_processor_records_processed_total` - Records processed
- `kafka_streams_state_store_*` - State store metrics
- `engagement_scores_produced_total` - Scores published
- `engagement_alerts_triggered_total` - Alert threshold crossings

## Health Checks

Spring Boot Actuator endpoints:

- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe

## Profiles

| Profile  | Port | Log Level | Use Case              |
|----------|------|-----------|-----------------------|
| `dev`    | 8082 | DEBUG     | Local development     |
| `prod`   | 8080 | WARN      | Production deployment |
| `docker` | 8080 | INFO      | Container deployment  |

## Dependencies

- Spring Boot 3.x
- Spring Kafka + Kafka Streams
- Confluent Kafka Streams Avro Serde
- Micrometer + Prometheus
- edupulse-common (shared Avro events)
