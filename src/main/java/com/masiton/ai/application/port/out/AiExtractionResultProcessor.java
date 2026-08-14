package com.masiton.ai.application.port.out;

import java.util.UUID;
import java.time.OffsetDateTime;

import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;

/**
 * Handoff to E3-T06 candidate validation and snapshot persistence. Implementations must validate the
 * worker/attempt lease fence and persist the candidate, successful attempt, and terminal job transition
 * atomically. The worker remains fail-closed until such an implementation is available.
 */
public interface AiExtractionResultProcessor {
    boolean process(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                    OffsetDateTime finishedAt, AiVideoExtractionResult result);
}
