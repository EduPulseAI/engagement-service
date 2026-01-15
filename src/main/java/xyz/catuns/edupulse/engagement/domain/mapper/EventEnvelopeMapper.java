package xyz.catuns.edupulse.engagement.domain.mapper;

import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import xyz.catuns.edupulse.common.messaging.events.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;
import static org.mapstruct.MappingConstants.ComponentModel.SPRING;
import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(
        componentModel = SPRING,
        injectionStrategy = CONSTRUCTOR,
        unmappedTargetPolicy = IGNORE)
public abstract class EventEnvelopeMapper {

    @Value("${spring.application.name}")
    private String applicationName;

    public EventEnvelope.Builder envelopeBuilder() {
        return EventEnvelope.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSource(applicationName)
                .setSpecversion("1.0")
                .setTimestamp(Instant.now());
    }
}
