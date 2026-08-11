package com.masiton.ai.application.port.out;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.masiton.ai.application.port.out.TemporaryInputCipher.EncryptedInput;

public interface AiExtractionWorkerStore {

    Optional<ClaimedJob> claim(String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt,
                               int maxAttempts, OffsetDateTime quotaWindowStart, long quotaLimit);

    boolean heartbeat(UUID jobId, String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt);

    boolean recordRetryableFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                   OffsetDateTime finishedAt, String errorCategory);

    Optional<Integer> beginRetry(UUID jobId, String workerId, OffsetDateTime now,
                                 OffsetDateTime leaseExpiresAt, int maxAttempts,
                                 OffsetDateTime quotaWindowStart, long quotaLimit);

    boolean completeFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                            OffsetDateTime finishedAt, String errorCategory);

    boolean failWithoutAttempt(UUID jobId, String workerId, OffsetDateTime finishedAt, String errorCategory);

    int failExpiredExhausted(OffsetDateTime now, int maxAttempts);

    long quotaUsage(OffsetDateTime quotaWindowStart);

    record ClaimedJob(UUID jobId, URI videoUrl, EncryptedInput temporaryInput, int attemptNo) {
    }
}
