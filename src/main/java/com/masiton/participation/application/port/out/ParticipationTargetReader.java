package com.masiton.participation.application.port.out;

import java.util.UUID;

import com.masiton.participation.domain.ParticipationTargetType;

public interface ParticipationTargetReader {

    boolean targetExists(ParticipationTargetType targetType, UUID targetId);
}
