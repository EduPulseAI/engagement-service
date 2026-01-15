package xyz.catuns.edupulse.engagement.service.aggregate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.catuns.edupulse.engagement.domain.events.EnrichedEvent;
import xyz.catuns.edupulse.engagement.domain.model.StudentEngagementState;

/**
 * Aggregates quiz answers and session events into student engagement state
 */
@Component
@Slf4j
public class StudentEngagementAggregator {

    /**
     * Aggregate incoming event into student engagement state
     *
     * @param studentId Student identifier
     * @param event Incoming event (QuizAnswer or SessionEvent)
     * @param state Current aggregation state
     * @return Updated state
     */
    public StudentEngagementState aggregate(
            String studentId,
            EnrichedEvent event,
            StudentEngagementState state
    ) {

        // Initialize state if empty
        if (state.getStudentId() == null) {
            state.setStudentId(studentId);
            state.setSessionId(event.getSessionId());
        }

        // Process based on event type
        if ("quiz.answered".equals(event.getEventType())) {
            state.addQuizAnswer(event);
        } else if (event.getEventType().startsWith("session.")) {
            state.addSessionEvent(event);
        }

        log.debug("Aggregated event: studentId={}, type={}, navigationEvents={}",
                state.getStudentId(),
                event.getEventType(),
                state.getNavigationEvents()
        );

        return state;
    }

}
