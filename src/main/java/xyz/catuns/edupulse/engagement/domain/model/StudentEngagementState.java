package xyz.catuns.edupulse.engagement.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.catuns.edupulse.engagement.domain.events.EnrichedEvent;

import java.util.*;

/**
 * Stateful aggregation of student engagement signals within a time window
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentEngagementState {

    private static long RAPID_SUBMISSION_INTERVAL = 5000; // 5 seconds

    // Identity
    private String studentId;
    private String sessionId;
    private Long windowStart;
    private Long windowEnd;

    // Quiz answer metrics
    private int totalAnswers;
    private int correctAnswers;
    private int incorrectAnswers;
    @Builder.Default
    private List<Long> answerTimestamps = new ArrayList<>();
    @Builder.Default
    private List<Long> timeSpentValues = new ArrayList<>();  // timeSpentMs for each answer
    private long totalTimeSpent;

    // Session activity metrics
    private int navigationEvents;
    private int pauseEvents;
    private int resumeEvents;
    private long totalDwellTime;
    @Builder.Default
    private Set<String> pagesVisited = new HashSet<>();

    // Pattern detection
    private int consecutiveIncorrect;
    private int rapidSubmissions;  // Submissions < 5 seconds apart

    @Builder.Default
    private Map<String, Integer> skillTagAttempts = new HashMap<>();  // Track attempts per skill

    // Temporal tracking
    private Long firstEventTimestamp;
    private Long lastEventTimestamp;

    // Hints and support
    private int totalHintsUsed;


    /**
     * Add quiz answer to state
     */
    public void addQuizAnswer(EnrichedEvent event) {
        this.totalAnswers++;

        if (Boolean.TRUE.equals(event.getIsCorrect())) {
            this.correctAnswers++;
            this.consecutiveIncorrect = 0;  // Reset
        } else {
            this.incorrectAnswers++;
            this.consecutiveIncorrect++;
        }

        if (event.getTimeSpentMs() != null) {
            this.timeSpentValues.add(event.getTimeSpentMs());
            this.totalTimeSpent += event.getTimeSpentMs();
        }

        // Track timestamps
        this.answerTimestamps.add(event.getTimestamp().toEpochMilli());

        // Detect rapid submissions
        if (answerTimestamps.size() > 1) {
            int lastIndex = answerTimestamps.size() - 1;
            long timeDiff = answerTimestamps.get(lastIndex) - answerTimestamps.get(lastIndex - 1);
            if (timeDiff < RAPID_SUBMISSION_INTERVAL) {
                this.rapidSubmissions++;
            }
        }

        // Track skill attempts
        if (event.getSkillTag() != null) {
            this.skillTagAttempts.merge(event.getSkillTag(), 1, Integer::sum);
        }

        // Track hints
        if (event.getHintsUsed() != null) {
            this.totalHintsUsed += event.getHintsUsed();
        }

        updateTimestamps(event.getTimestamp().toEpochMilli());

    }

    /**
     * Add session event to state
     */
    public void addSessionEvent(EnrichedEvent event) {

        switch (event.getSessionEventType()) {
            case NAVIGATION -> {
                this.navigationEvents++;
                if (event.getPageId() != null) {
                    this.pagesVisited.add(event.getPageId());
                }
            }
            case PAUSED -> {
                this.pauseEvents++;
            }
            case RESUMED -> {
                this.resumeEvents++;
            }
            case DWELL -> {
                if (event.getDwellTimeMs() != null) {
                    this.totalDwellTime += event.getDwellTimeMs();
                }
            }
        }

        updateTimestamps(event.getTimestamp().toEpochMilli());
    }

    private void updateTimestamps(Long eventTimestamp) {
        if (this.firstEventTimestamp == null || eventTimestamp < this.firstEventTimestamp) {
            this.firstEventTimestamp = eventTimestamp;
        }
        if (this.lastEventTimestamp == null || eventTimestamp > this.lastEventTimestamp) {
            this.lastEventTimestamp = eventTimestamp;
        }
    }

    /* ──────────────────────────────────────────────
                    Computed metrics
    ────────────────────────────────────────────── */

    public double getCorrectnessRate() {
        if (totalAnswers == 0) return 0.0;
        return (double) correctAnswers / totalAnswers;
    }

    public double getAverageTimeSpent() {
        if (timeSpentValues.isEmpty()) return 0.0;
        return (double) totalTimeSpent / timeSpentValues.size();
    }

    public double getQuestionsPerMinute() {
        if (firstEventTimestamp == null || lastEventTimestamp == null) return 0.0;
        long durationMs = lastEventTimestamp - firstEventTimestamp;
        if (durationMs == 0) return 0.0;
        double durationMinutes = durationMs / 60000.0;
        return totalAnswers / durationMinutes;
    }

    public boolean hasRapidIncorrectPattern() {
        return consecutiveIncorrect >= 3 && rapidSubmissions >= 2;
    }

    public boolean isStrugglingPattern() {
        return getAverageTimeSpent() > 20000 && getCorrectnessRate() < 0.5;
    }

    public int getUniqueSkillsAttempted() {
        return skillTagAttempts.size();
    }

    public long getActiveTimeMs() {
        if (firstEventTimestamp == null || lastEventTimestamp == null) return 0L;
        return lastEventTimestamp - firstEventTimestamp;
    }
}
