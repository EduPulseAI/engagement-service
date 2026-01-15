package xyz.catuns.edupulse.engagement.domain.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@Mapping(target = "eventId", source = "envelope.id")
@Mapping(target = "studentId", source = "envelope.studentId")
@Mapping(target = "sessionId", source = "envelope.sessionId")
@Mapping(target = "timestamp", source = "envelope.timestamp")
@Mapping(target = "eventType", source = "envelope.type")
@Mapping(target = "source", source = "envelope.source")
public @interface EnvelopeSourceMapping {
}
