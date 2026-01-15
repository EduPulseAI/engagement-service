package xyz.catuns.edupulse.engagement.domain.model;

import lombok.Getter;

@Getter
public enum BehavioralPattern {
    NORMAL("normal"),
    RAPID_INCORRECT_SUBMISSIONS("rapid_incorrect_submissions"),
    STRUGGLING_EXTENSIVELY("struggling_extensively"),
    RUSHING_THROUGH("rushing_through"),
    EXCESSIVE_HINTS("excessive_hints"),
    FREQUENT_PAUSES("frequent_pauses"),
    MINIMAL_ENGAGEMENT("minimal_engagement");

    private final String code;

    BehavioralPattern(String code) {
        this.code = code;
    }

}
