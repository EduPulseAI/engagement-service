package xyz.catuns.edupulse.engagement.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import xyz.catuns.edupulse.common.messaging.events.quiz.QuizAnswer;
import xyz.catuns.edupulse.common.messaging.events.session.SessionEvent;
import xyz.catuns.edupulse.engagement.domain.events.EnrichedEvent;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;
import static org.mapstruct.MappingConstants.ComponentModel.SPRING;
import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(
        componentModel = SPRING,
        injectionStrategy = CONSTRUCTOR,
        unmappedTargetPolicy = IGNORE)
public interface EnrichedEventMapper {

    @EnvelopeSourceMapping
    @Mapping(target = "questionId", source = "questionId")
    @Mapping(target = "isCorrect", source = "isCorrect")
    @Mapping(target = "timeSpentMs", source = "timeSpentMs")
    @Mapping(target = "hintsUsed", source = "contextualData.hintsUsed")
    @Mapping(target = "skillTag", source = "skillTag")
    @Mapping(target = "difficultyLevel", source = "difficultyLevel")
    EnrichedEvent fromQuizAnswer(QuizAnswer quizAnswer);

    @EnvelopeSourceMapping
    @Mapping(target = "sessionEventType", source = "eventType")
    @Mapping(target = "pageId", source = "pageId")
    @Mapping(target = "dwellTimeMs", source = "dwellTimeMs")
    EnrichedEvent fromSessionEvent(SessionEvent sessionEvent);
}
