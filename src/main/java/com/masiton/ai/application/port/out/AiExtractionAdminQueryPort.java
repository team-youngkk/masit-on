package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

public interface AiExtractionAdminQueryPort {
    Page list(String executionStatus, String source, String reviewStatus, int offset, int size);
    Optional<Detail> detail(UUID jobId);
    Optional<RetryTarget> retryTarget(UUID jobId);
    Optional<ReviewTarget> reviewSnapshot(UUID jobId);
    Optional<ReviewTarget> reviewTarget(UUID jobId);
    UUID override(UUID snapshotId, String expectedStatus, UUID adminId, String reason, String decision);
    void markRegisteredContent(UUID snapshotId, RegisteredContent content);
    List<TagDecision> connectConfirmedTags(UUID snapshotId, UUID visitId, List<TagDecision> decisions);
    void appendTagOverrides(UUID snapshotId, UUID adminId, String reason, List<TagDecision> decisions);

    record Page(List<AiExtractionJobView> items, long totalElements) { }
    record Detail(AiExtractionJobView job, JsonNode candidateFields, JsonNode candidateTags, JsonNode fieldConfidences,
                  JsonNode evidence, JsonNode missingFields, String errorCategory, Boolean retryable,
                  List<Attempt> attempts) { }
    record Attempt(int attemptNo, String outcome, String errorCategory, OffsetDateTime startedAt, OffsetDateTime finishedAt) { }
    record RetryTarget(String videoUrl, String executionStatus, String resultCompleteness) { }
    record ReviewTarget(UUID snapshotId, String reviewStatus, UUID jobId, String channelId, String videoId,
                        String videoUrl, JsonNode candidateFields, JsonNode candidateTags, JsonNode fieldConfidences, JsonNode evidence,
                        RegisteredContent registeredContent) { }
    record RegisteredContent(UUID restaurantId, boolean restaurantCreated, UUID creatorId, boolean creatorCreated,
                             UUID videoId, boolean videoCreated, UUID visitId, boolean visitCreated,
                             UUID registrationSnapshotId) {
        public RegisteredContent(UUID restaurantId, boolean restaurantCreated, UUID creatorId, boolean creatorCreated,
                                 UUID videoId, boolean videoCreated, UUID visitId, boolean visitCreated) {
            this(restaurantId, restaurantCreated, creatorId, creatorCreated, videoId, videoCreated, visitId,
                    visitCreated, null);
        }
    }
    record TagDecision(String candidateTagId, String decision, String tagCode) { }
}
