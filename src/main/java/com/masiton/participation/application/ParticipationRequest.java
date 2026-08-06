package com.masiton.participation.application;

import java.util.Map;
import java.util.UUID;

import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;

public final class ParticipationRequest {

    private ParticipationRequest() {
    }

    public record Submission(
            ParticipationTargetType targetType,
            Map<String, Object> candidate,
            String description,
            String evidenceUrl
    ) {
    }

    public record Report(
            ParticipationTargetType targetType,
            UUID targetId,
            ReportType reportType,
            String description,
            String evidenceUrl
    ) {
    }
}
