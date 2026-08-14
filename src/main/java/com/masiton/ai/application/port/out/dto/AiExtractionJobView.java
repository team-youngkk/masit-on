package com.masiton.ai.application.port.out.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiExtractionJobView(
        UUID jobId,
        String source,
        String channelId,
        String videoId,
        String videoUrl,
        String executionStatus,
        String resultCompleteness,
        String reviewStatus,
        String provider,
        String modelVersion,
        String promptVersion,
        String schemaVersion,
        int attemptCount,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        boolean reused
) {
}
