package xyz.catuns.edupulse.engagement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.catuns.edupulse.common.messaging.events.EventEnvelope;
import xyz.catuns.edupulse.common.messaging.events.engagement.EngagementScore;
import xyz.catuns.edupulse.common.messaging.events.engagement.EngagementTrend;
import xyz.catuns.edupulse.common.messaging.events.engagement.ScoreComponents;
import xyz.catuns.edupulse.engagement.config.properties.ScoringProperties;
import xyz.catuns.edupulse.engagement.domain.mapper.EventEnvelopeMapper;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;

@Service
@RequiredArgsConstructor
public class EngagementScoringService {

    private final ScoringProperties config;
    private final EventEnvelopeMapper mapper;

    public EngagementScore calculate(StudentEngagementState aggregate) {
        double accuracyScore = calculateAccuracyScore(aggregate);
        double dwellScore = calculateDwellScore(aggregate);
        double pacingScore = calculatePacingScore(aggregate);

        // Build score components
        ScoreComponents components = ScoreComponents.newBuilder()
                .setAccuracyScore(accuracyScore)
                .setDwellScore(dwellScore)
                .setPacingScore(pacingScore)
                .setAttentionScore(null)  // Not implemented yet
                .build();

        // Calculate weighted composite score
        double compositeScore = calculateCompositeScore(accuracyScore, dwellScore, pacingScore);

        // Apply pattern-based penalties
        double finalScore = applyPatternPenalties(compositeScore, aggregate);

        // Clamp to [0.0, 1.0]
        finalScore = Math.max(0.0, Math.min(1.0, finalScore));

        // Determine trend
        EngagementTrend trend = determineTrend(finalScore, aggregate);

        // Check alert threshold
        boolean alertCrossed = finalScore < config.getThresholds().getAlert();

        // Build EventEnvelope
        EventEnvelope envelope = mapper.envelopeBuilder()
                .setType("engagement.scored")
                .setStudentId(aggregate.getStudentId())
                .setSessionId(aggregate.getSessionId())
                .setCorrelationId(null)
                .build();

        // Build EngagementScore
        return EngagementScore.newBuilder()
                .setEnvelope(envelope)
                .setScore(finalScore)
                .setScoreComponents(components)
                .setTrend(trend)
                .setAlertThresholdCrossed(alertCrossed)
                .build();
    }

    private EngagementTrend determineTrend(double score, StudentEngagementState aggregate) {
        if (score < config.getThresholds().getAlert()) {
            return EngagementTrend.CRITICAL;
        }

        // Declining - warning zone
        if (score < config.getThresholds().getYellow()) {
            return EngagementTrend.DECLINING;
        }

        // For RISING vs STABLE, we'd need historical context
        // For now, use heuristics based on current window

        // Good performance indicators
        boolean goodAccuracy = aggregate.getCorrectnessRate() > 0.7;
        boolean healthyPace = aggregate.getAverageTimeSpent() >= config.getThresholds().getTime().getRushingMs()
                && aggregate.getAverageTimeSpent() <= config.getThresholds().getTime().getStrugglingMs();

        if (goodAccuracy && healthyPace && score >= 0.8) {
            return EngagementTrend.RISING;
        }

        return EngagementTrend.STABLE;
    }

    private double applyPatternPenalties(double baseScore, StudentEngagementState aggregate) {
        double adjustedScore = baseScore;

        // Rapid incorrect submissions penalty
//        if (aggregate.hasRapidIncorrectPattern()) {
//            adjustedScore *= config.getRapidIncorrectPenalty();
//            log.debug("Applied rapid incorrect penalty: {} -> {}", baseScore, adjustedScore);
//        }

        // Struggling penalty
//        if (aggregate.isStrugglingPattern()) {
//            adjustedScore *= config.getStrugglingPenalty();
//            log.debug("Applied struggling penalty: {} -> {}", baseScore, adjustedScore);
//        }

        // Excessive hints penalty
//        if (aggregate.getTotalAnswers() > 0) {
//            double avgHints = (double) aggregate.getTotalHintsUsed() / aggregate.getTotalAnswers();
//            if (avgHints >= config.getExcessiveHintsThreshold()) {
//                adjustedScore *= config.getHintPenaltyFactor();
//                log.debug("Applied excessive hints penalty: {} -> {}", baseScore, adjustedScore);
//            }
//        }

        return adjustedScore;
    }

    private double calculatePacingScore(StudentEngagementState aggregate) {
        if (aggregate.getTotalAnswers() == 0) {
            return 1.0;  // Neutral if no data
        }

        double questionsPerMinute = aggregate.getQuestionsPerMinute();
        double idealPace = config.getThresholds().getPacing().getExpectedQuestionsPerMinute();
        double tolerance = config.getThresholds().getPacing().getTolerancePercent();

        double lowerBound = idealPace * (1 - tolerance);
        double upperBound = idealPace * (1 + tolerance);

        // Within tolerance
        if (questionsPerMinute >= lowerBound && questionsPerMinute <= upperBound) {
            return 1.0;
        }

        // Outside tolerance
        return 0.7;
    }

    private double calculateDwellScore(StudentEngagementState aggregate) {
        if (aggregate.getTotalAnswers() == 0) {
            return 1.0;  // Neutral if no quiz data
        }

        double avgTimeSpent = aggregate.getAverageTimeSpent();

        // Too long (struggling)
        if (avgTimeSpent > config.getThresholds().getTime().getStrugglingMs()) {
            return 0.3;
        }

        // Too fast (rushing)
        if (avgTimeSpent < config.getThresholds().getTime().getRushingMs()) {
            return 0.5;
        }

        // Healthy pace
        return 1.0;
    }

    private double calculateAccuracyScore(StudentEngagementState aggregate) {
        if (aggregate.getTotalAnswers() == 0) {
            return 0.0;
        }

        return aggregate.getCorrectnessRate();
    }

    private double calculateCompositeScore(double accuracyScore, double dwellScore, double pacingScore) {
        return (accuracyScore * config.getWeights().getAccuracy()) +
                (dwellScore * config.getWeights().getDwell()) +
                (pacingScore * config.getWeights().getPacing());
    }
}
