package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;

import tools.jackson.databind.ObjectMapper;

/**
 * AI 결과의 짧은 저장 트랜잭션을 소유한다. 외부 검증은 호출자에서 끝낸 뒤 이
 * 서비스에 들어오며, 이 안에서만 후보·정식 Entity·태그·작업 종료를 함께 커밋한다.
 */
@Service
class AiExtractionResultCommitService {

    private final AiExtractionResultStore resultStore;
    private final AutoRegisterVerifiedContentUseCase autoRegister;
    private final ObjectMapper objectMapper;

    AiExtractionResultCommitService(AiExtractionResultStore resultStore,
                                    AutoRegisterVerifiedContentUseCase autoRegister,
                                    ObjectMapper objectMapper) {
        this.resultStore = resultStore;
        this.autoRegister = autoRegister;
        this.objectMapper = objectMapper;
    }

    @Transactional
    boolean persistBlocked(ProcessCommand command) {
        Optional<AiExtractionResultStore.ProcessingJob> job = resultStore.lockProcessingJob(
                command.jobId(), command.workerId(), command.attemptNo());
        if (job.isEmpty()) {
            return false;
        }
        UUID snapshotId = insertSnapshot(command, command.reviewStatus(), command.blockReason());
        insertTagReviews(snapshotId, command.tags(), command.finishedAt());
        resultStore.completeSuccess(command.jobId(), command.workerId(), command.attemptNo(),
                command.resultCompleteness(), command.attemptStartedAt(), command.finishedAt(),
                command.providerRequestId());
        return true;
    }

    @Transactional
    boolean persistConfirmed(ProcessCommand command,
                             AutoRegisterVerifiedContentUseCase.VerifiedContentCommand registrationCommand) {
        Optional<AiExtractionResultStore.ProcessingJob> job = resultStore.lockProcessingJob(
                command.jobId(), command.workerId(), command.attemptNo());
        if (job.isEmpty()) {
            return false;
        }
        UUID snapshotId = insertSnapshot(command, "AUTO_CONFIRMED", null);
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = autoRegister.register(registrationCommand);
        resultStore.markRegisteredContent(snapshotId, registration.restaurantId(), registration.restaurantCreated(),
                registration.creatorId(), registration.creatorCreated(), registration.videoId(), registration.videoCreated(),
                registration.visitId(), registration.visitCreated());
        insertTagReviewsAndVisitTags(snapshotId, command.tags(), registration.visitId(), command.finishedAt());
        resultStore.completeSuccess(command.jobId(), command.workerId(), command.attemptNo(),
                command.resultCompleteness(), command.attemptStartedAt(), command.finishedAt(),
                command.providerRequestId());
        return true;
    }

    private UUID insertSnapshot(ProcessCommand command, String reviewStatus, String reviewReason) {
        return resultStore.insertSnapshot(command.jobId(), resultStore.nextSnapshotVersion(command.jobId()),
                command.candidateFields(), command.candidateTags(), command.fieldConfidences(),
                command.evidence(), command.missingFields(), reviewStatus, reviewReason,
                command.finishedAt(), command.finishedAt());
    }

    private void insertTagReviews(UUID snapshotId, List<AiTagCandidate> tags, OffsetDateTime reviewedAt) {
        for (AiTagCandidate tag : tags) {
            String reason = tag.reason() == null ? "CANDIDATE_BLOCKED" : tag.reason();
            resultStore.insertTagReview(snapshotId, tag.candidateTagId(), "AUTO_REJECT", null,
                    reason, reviewedAt);
        }
    }

    private void insertTagReviewsAndVisitTags(UUID snapshotId, List<AiTagCandidate> tags, UUID visitId,
                                              OffsetDateTime reviewedAt) {
        List<AiTagCandidate> orderedTags = tags.stream()
                .sorted(Comparator.comparing(AiTagCandidate::normalizedCode))
                .toList();
        for (AiTagCandidate tag : orderedTags) {
            if (!tag.autoConnectable()) {
                resultStore.insertTagReview(snapshotId, tag.candidateTagId(), "AUTO_REJECT", null,
                        tag.reason(), reviewedAt);
                continue;
            }
            Optional<AiExtractionResultStore.TagDefinition> existing = resultStore.findTagForUpdate(tag.normalizedCode());
            boolean merged = existing.isPresent();
            AiExtractionResultStore.TagDefinition definition = existing.orElse(null);
            if (definition == null) {
                Optional<AiExtractionResultStore.TagDefinition> inserted = resultStore.insertTagIfAbsent(
                        UUID.randomUUID(), tag.normalizedCode(), tag.tagType(), tag.label(), tag.aliasesJson(),
                        "AI_AUTO", snapshotId, reviewedAt);
                definition = inserted.orElseGet(() -> {
                    AiExtractionResultStore.TagDefinition concurrent = resultStore.findTagForUpdate(tag.normalizedCode())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Concurrent AI tag result was not found."));
                    return concurrent;
                });
                merged = inserted.isEmpty();
            }
            if (!"ACTIVE".equals(definition.status()) || !definition.tagType().equals(tag.tagType())
                    || !AiTagPolicy.matchesApprovedLabel(tag.label(), definition, objectMapper)) {
                resultStore.insertTagReview(snapshotId, tag.candidateTagId(), "AUTO_REJECT", null,
                        "TAG_POLICY", reviewedAt);
                continue;
            }
            String decision = merged ? "AUTO_MERGE" : "AUTO_ACCEPT";
            resultStore.insertTagReview(snapshotId, tag.candidateTagId(), decision,
                    "AUTO_MERGE".equals(decision) ? definition.id() : null, tag.reason(), reviewedAt);
            resultStore.insertVisitTag(visitId, definition.id(), tag.confidence(), tag.evidenceJson(),
                    tag.extractorVersion(), reviewedAt);
        }
    }

    record ProcessCommand(
            UUID jobId,
            String workerId,
            int attemptNo,
            OffsetDateTime attemptStartedAt,
            OffsetDateTime finishedAt,
            String providerRequestId,
            String resultCompleteness,
            String candidateFields,
            String candidateTags,
            String fieldConfidences,
            String evidence,
            String missingFields,
            String blockReason,
            String reviewStatus,
            List<AiTagCandidate> tags) {
    }

    record AiTagCandidate(
            String candidateTagId,
            String tagType,
            String normalizedCode,
            String label,
            java.math.BigDecimal confidence,
            String evidenceJson,
            String aliasesJson,
            String extractorVersion,
            String decision,
            String reason,
            boolean autoConnectable,
            UUID existingDefinitionId) {
    }
}
