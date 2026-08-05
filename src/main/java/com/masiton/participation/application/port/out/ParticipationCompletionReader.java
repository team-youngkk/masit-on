package com.masiton.participation.application.port.out;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationTargetType;

public interface ParticipationCompletionReader {

    boolean isCompleted(
            ModerationActionType actionType, ParticipationTargetType targetType,
            UUID targetId, OffsetDateTime acceptedAt, Map<String, Object> candidate);
}
