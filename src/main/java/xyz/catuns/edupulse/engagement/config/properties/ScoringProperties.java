package xyz.catuns.edupulse.engagement.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringProperties {

    /*
     * Scoring Weights
     */
    private Weight weights = new Weight();
    /*
     * Scoring Thresholds
     */
    private Threshold thresholds = new Threshold();
    /*
     * Scoring Window
     */
    private Window window = new Window();
    /*
     * Scoring Baseline
     */
    private Baseline baseline = new Baseline();

    @Data
    private static class Weight {
        private float dwell = 0.3f;
        private float accuracy = 0.4f;
        private float pacing = 0.3f;
        private float attention = 0.0f; // Not implemented yet;
    }

    @Data
    private static class Threshold {
        /**
         *
         */
        private float alert = 0.4f;
        /**
         *
         */
        private float green = 0.7f;
        /**
         *
         */
        private float yellow = 0.4f;
    }

    @Data
    private static class Window {
        /**
         *
         */
        private long durationSeconds = 60;
        /**
         *
         */
        private long gracePeriodSeconds = 5;
    }

    @Data
    private static class Baseline {
        /**
         *
         */
        private long expectedTimePerQuestionSeconds = 120;
        /**
         *
         */
        private float expectedQuestionsPerMinute = 0.5f;
    }
}
