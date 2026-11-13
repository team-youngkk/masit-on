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
        boolean reused,
        String errorCategory,
        Integer failureHttpStatus
) {
    public AiExtractionJobView(UUID jobId, String source, String channelId, String videoId, String videoUrl,
                               String executionStatus, String resultCompleteness, String reviewStatus,
                               String provider, String modelVersion, String promptVersion, String schemaVersion,
                               int attemptCount, OffsetDateTime createdAt, OffsetDateTime startedAt,
                               OffsetDateTime finishedAt, boolean reused) {
        this(jobId, source, channelId, videoId, videoUrl, executionStatus, resultCompleteness, reviewStatus,
                provider, modelVersion, promptVersion, schemaVersion, attemptCount, createdAt, startedAt,
                finishedAt, reused, null, null);
    }
}
