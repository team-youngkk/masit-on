package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface YoutubeChannelBackfillRunStore {

    Optional<Run> find(UUID creatorId, UUID runId);

    Optional<StartRun> createOrReuse(UUID creatorId, OffsetDateTime now);

    Optional<ClaimedRun> claim(OffsetDateTime now, OffsetDateTime leaseExpiresAt, String leaseOwner);

    boolean isClaimActive(UUID runId, String leaseOwner, OffsetDateTime now);

    void recordVideo(UUID runId, String videoId, boolean reused, OffsetDateTime now);

    void completePage(UUID runId, String leaseOwner, int scanned,
                      String nextPageToken, boolean completed, boolean limitReached, OffsetDateTime now);

    void fail(UUID runId, String leaseOwner, String errorCategory, OffsetDateTime now);

    void stop(UUID creatorId, UUID runId, OffsetDateTime now);

    void stopRunsForInactiveWatches(OffsetDateTime now);

    record Run(UUID runId, UUID creatorId, String status, long scannedCount, long submittedCount,
               long reusedCount, String lastErrorCategory, OffsetDateTime createdAt,
               OffsetDateTime updatedAt) { }

    record ClaimedRun(UUID runId, UUID creatorId, String channelId, String pageToken, int pageCount,
                      long scannedCount, String leaseOwner) { }
    record StartRun(Run run, boolean reused) { }
}
