package com.masiton.participation.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReportType {
    ERROR(EnumSet.allOf(ParticipationTargetType.class)),
    CLOSED(EnumSet.of(ParticipationTargetType.RESTAURANT)),
    UNAVAILABLE(EnumSet.of(ParticipationTargetType.CREATOR, ParticipationTargetType.VIDEO)),
    WRONG_RELATIONSHIP(EnumSet.of(ParticipationTargetType.VISIT_RELATIONSHIP)),
    INAPPROPRIATE_CONTENT(EnumSet.allOf(ParticipationTargetType.class));

    private final Set<ParticipationTargetType> targets;

    ReportType(Set<ParticipationTargetType> targets) {
        this.targets = targets;
    }

    public boolean supports(ParticipationTargetType targetType) {
        return targets.contains(targetType);
    }
}
