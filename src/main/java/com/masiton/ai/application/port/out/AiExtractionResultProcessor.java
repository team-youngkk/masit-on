package com.masiton.ai.application.port.out;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;

/**
 * Handoff to E3-T06 candidate validation and snapshot persistence. Implementations must validate the
 * worker/attempt lease fence and persist the candidate, successful attempt, and terminal job transition
 * atomically. The worker remains fail-closed until such an implementation is available.
 */
public interface AiExtractionResultProcessor {
    boolean process(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                    OffsetDateTime finishedAt, AiVideoExtractionResult result);

    default boolean process(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                            OffsetDateTime finishedAt, AiVideoExtractionResult result,
                            SupplementProvider supplementProvider) {
        return process(jobId, workerId, attemptNo, attemptStartedAt, finishedAt, result);
    }

    default Optional<Boolean> resumePending(UUID jobId, String workerId, int attemptNo,
                                            OffsetDateTime attemptStartedAt, OffsetDateTime finishedAt,
                                            SupplementProvider supplementProvider) {
        return Optional.empty();
    }

    @FunctionalInterface
    interface SupplementProvider {
        Optional<SupplementResult> extract(UUID jobId, UUID retryId, String supplementText);
    }

    record SupplementResult(int attemptNo, OffsetDateTime attemptStartedAt, OffsetDateTime finishedAt,
                            AiVideoExtractionResult result, boolean providerSucceeded) {
    }
}
