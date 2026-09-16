package com.masiton.ai.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface YoutubeChannelBackfillUseCase {

    StartResult start(UUID creatorId);

    RunStatus get(UUID creatorId, UUID runId);

    void stop(UUID creatorId, UUID runId);

    void poll();

    void scheduleDue();

    record StartResult(UUID runId, String status, boolean reused) { }

    record RunStatus(UUID runId, String status, long scannedCount, long submittedCount,
                     long reusedCount, String lastErrorCategory, OffsetDateTime createdAt,
                     OffsetDateTime updatedAt) { }
}
