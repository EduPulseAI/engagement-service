package xyz.catuns.edupulse.engagement.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

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


    /**
     * properties must add up to 1.0
     */
    @Data
    public static class Weight {
        /**
         * idle time spent
         */
        private float dwell = 0.3f;
        /**
         * correct answers
         */
        private float accuracy = 0.4f;
        /**
         * frequency of answers received
         */
        private float pacing = 0.3f;
        /**
         * arbitrary field to consider later
         */
        private float attention = 0.0f; // Not implemented yet;
    }

    @Data
    public static class Threshold {
        /**
         * should trigger alert
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

        /*
         * Time Threshold
         */
        @NestedConfigurationProperty
        private TimeThreshold time = new TimeThreshold();
        /*
         * Pacing Threshold
         */
        @NestedConfigurationProperty
        private PacingThreshold pacing = new PacingThreshold();
        /*
         * Pattern Threshold
         */
        @NestedConfigurationProperty
        private PatternThreshold pattern = new PatternThreshold();

        @Data
        public static class TimeThreshold {
            /**
             * How much time is considered struggling
             */
            private long strugglingMs = 15000;
            /**
             * How much time is considered rushing
             */
            private long rushingMs = 5000;
        }

        @Data
        public static class PatternThreshold {
            /**
             * Seconds between answers
             */
            private int rapidSubmissionMs = 5000;
            /**
             * Consecutive incorrect answers
             */
            private int consecutiveIncorrect = 3;
        }

        @Data
        public static class PacingThreshold {
            /**
             *
             */
            private float expectedQuestionsPerMinute = 0.5f;
            /**
             *
             */
            private float tolerancePercent = 0.2f;
        }
    }

    @Data
    public static class Window {
        /**
         *
         */
        private long durationSeconds = 60;
        /**
         *
         */
        private long gracePeriodSeconds = 5;
    }

}
