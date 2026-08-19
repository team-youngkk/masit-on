package com.masiton.ai.application.port.out;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 결과 처리기가 사용하는 AI 후보·작업 상태 저장 Port다.
 *
 * <p>구현체는 호출자의 트랜잭션에 참여하며, 결과 처리기는 이 Port 외에 JDBC나
 * Spring Data를 직접 사용하지 않는다.</p>
 */
public interface AiExtractionResultStore {

    Optional<ProcessingJob> lockProcessingJob(UUID jobId, String workerId, int attemptNo);

    int nextSnapshotVersion(UUID jobId);

    UUID insertSnapshot(UUID jobId, int snapshotVersion, String candidateFields, String candidateTags,
                        String fieldConfidences, String evidence, String missingFields, boolean candidateTruncated,
                        String reviewStatus, String reviewReason, OffsetDateTime reviewedAt,
                        OffsetDateTime createdAt);

    Optional<TagDefinition> findTag(String tagCode);

    Optional<TagDefinition> findTagForUpdate(String tagCode);

    Optional<TagDefinition> insertTagIfAbsent(UUID id, String tagCode, String tagType, String displayName,
                                               String aliases, String source, UUID snapshotId,
                                               OffsetDateTime createdAt);

    void insertTagReview(UUID snapshotId, String candidateTagId, String decision, UUID replacementTagDefinitionId,
                         String reason, OffsetDateTime reviewedAt);

    void insertVisitTag(UUID snapshotId, UUID visitId, UUID tagDefinitionId, java.math.BigDecimal confidence, String evidence,
                        String extractorVersion, OffsetDateTime createdAt);

    void markRegisteredContent(UUID snapshotId, UUID restaurantId, boolean restaurantCreated,
                               UUID creatorId, boolean creatorCreated, UUID videoId, boolean videoCreated,
                               UUID visitId, boolean visitCreated);

    void completeSuccess(UUID jobId, String workerId, int attemptNo, String resultCompleteness,
                         OffsetDateTime attemptStartedAt, OffsetDateTime finishedAt, String providerRequestId);

    record ProcessingJob(UUID jobId, String channelId, String videoId, URI videoUrl) {
    }

    record TagDefinition(UUID id, String tagCode, String tagType, String displayName, String aliases,
                         String status) {
    }
}
