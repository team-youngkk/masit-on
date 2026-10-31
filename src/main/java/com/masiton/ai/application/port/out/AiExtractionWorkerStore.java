package com.masiton.ai.application.port.out;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.masiton.ai.application.port.out.TemporaryInputCipher.EncryptedInput;

public interface AiExtractionWorkerStore {

    Optional<ClaimedJob> claim(String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt,
                               int maxAttempts, OffsetDateTime quotaWindowStart, long quotaLimit);

    /** 만료 lease를 가진 최신 Snapshot의 미완료 보완 claim을 Provider 시도 없이 다시 소유한다. */
    Optional<ClaimedJob> claimRecovery(String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt);

    boolean heartbeat(UUID jobId, String workerId, OffsetDateTime now, OffsetDateTime leaseExpiresAt);

    boolean recordRetryableFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                   OffsetDateTime finishedAt, String errorCategory);

    default boolean recordRetryableFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                           OffsetDateTime finishedAt, String errorCategory, Integer httpStatus) {
        return recordRetryableFailure(jobId, workerId, attemptNo, startedAt, finishedAt, errorCategory);
    }

    boolean recordSuccessfulSupplementAttempt(UUID jobId, String workerId, int attemptNo,
                                              OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                              String providerRequestId);

    Optional<Integer> beginRetry(UUID jobId, String workerId, OffsetDateTime now,
                                 OffsetDateTime leaseExpiresAt, int maxAttempts,
                                 OffsetDateTime quotaWindowStart, long quotaLimit);

    /** #421 자동 보완 Provider 호출을 기존 quota 잠금 아래에서 원자 예약한다. 작업 전체 호출 상한은 4회다. */
    Optional<Integer> reserveSupplementProviderCall(UUID jobId, String workerId, OffsetDateTime now,
                                                     OffsetDateTime leaseExpiresAt,
                                                     OffsetDateTime quotaWindowStart, long quotaLimit);

    Optional<Integer> reserveSupplementProviderCall(UUID jobId, String workerId, UUID retryId,
                                                     OffsetDateTime now, OffsetDateTime leaseExpiresAt,
                                                     OffsetDateTime quotaWindowStart, long quotaLimit);

    boolean completeFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                            OffsetDateTime finishedAt, String errorCategory);

    default boolean completeFailure(UUID jobId, String workerId, int attemptNo, OffsetDateTime startedAt,
                                    OffsetDateTime finishedAt, String errorCategory, Integer httpStatus) {
        return completeFailure(jobId, workerId, attemptNo, startedAt, finishedAt, errorCategory);
    }

    boolean failWithoutAttempt(UUID jobId, String workerId, OffsetDateTime finishedAt, String errorCategory);

    int failQueuedForQuota(OffsetDateTime finishedAt);

    int failExpiredExhausted(OffsetDateTime now, int maxAttempts);

    long quotaUsage(OffsetDateTime quotaWindowStart);

    record ClaimedJob(UUID jobId, URI videoUrl, EncryptedInput temporaryInput, int attemptNo) {
    }
}
