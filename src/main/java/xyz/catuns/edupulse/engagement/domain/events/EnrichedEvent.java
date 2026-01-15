package xyz.catuns.edupulse.engagement.domain.events;

import lombok.Data;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEventType;

import java.time.Instant;

@Data
public class EnrichedEvent {
    // Common fields from EventEnvelope
    private String eventId;
    private String studentId;
    private String sessionId;
    private Instant timestamp;
    private String eventType;  // "quiz.answered" or "session.navigation", etc.

    // Quiz-specific fields (null if from session event)
    private String questionId;
    private Boolean isCorrect;
    private Long timeSpentMs;
    private Integer hintsUsed;
    private String skillTag;
    private Integer difficultyLevel;

    // Session-specific fields (null if from quiz event)
    private SessionEventType sessionEventType;  // STARTED, NAVIGATION, DWELL, PAUSED, etc.
    private String pageId;
    private Long dwellTimeMs;

    // Metadata
    private String source;  // "quiz-service" or "session-service"
}
