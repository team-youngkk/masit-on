package com.masiton.ai.application;

import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class AdminAiExtractionQueryService {
    private final AiExtractionAdminQueryPort port;
    private final VerifyAiContentCandidateUseCase verifier;
    private final AdminAiExtractionReviewCommitService reviewCommit;

    public AdminAiExtractionQueryService(AiExtractionAdminQueryPort port,
                                         VerifyAiContentCandidateUseCase verifier,
                                         AdminAiExtractionReviewCommitService reviewCommit) {
        this.port = port;
        this.verifier = verifier;
        this.reviewCommit = reviewCommit;
    }
    @Transactional(readOnly = true)
    public AiExtractionAdminQueryPort.Page list(String executionStatus, String source, String reviewStatus, int page, int size) {
        return port.list(executionStatus, source, reviewStatus, (page - 1) * size, size);
    }
    @Transactional(readOnly = true)
    public AiExtractionAdminQueryPort.Detail detail(UUID jobId) { return port.detail(jobId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)); }
    @Transactional(readOnly = true)
    public String retryUrl(UUID jobId) {
        AiExtractionAdminQueryPort.RetryTarget target = port.retryTarget(jobId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"FAILED".equals(target.executionStatus()) && !("SUCCEEDED".equals(target.executionStatus()) && "PARTIAL".equals(target.resultCompleteness())))
            throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_RETRY_NOT_ALLOWED", "The job is not retryable.");
        return target.videoUrl();
    }
    public void review(UUID jobId, String decision, String expected, UUID adminId, String reason, List<AiExtractionAdminQueryPort.TagDecision> tags) {
        if (decision == null || expected == null || reason == null || reason.isBlank() || reason.trim().length() > 1_000) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "review", "decision, expectedReviewStatus and reason are required.");
        }
        AiExtractionAdminQueryPort.ReviewTarget target = port.reviewTarget(jobId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!target.reviewStatus().equals(expected)) throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_REVIEW_STALE", "Review status is stale.");
        validateTagDecisions(target, tags);
        boolean permitted = "DISCARD".equals(decision) && ("AUTO_BLOCKED".equals(expected) || "AUTO_REJECTED".equals(expected));
        if ("CONFIRM".equals(decision) && "AUTO_BLOCKED".equals(expected)) {
            reviewCommit.confirm(jobId, expected, adminId, reason, tags, registrationCommand(target));
            permitted = true;
        } else if ("ROLLBACK".equals(decision) && "AUTO_CONFIRMED".equals(expected)) {
            reviewCommit.rollback(jobId, expected, adminId, reason, tags);
            permitted = true;
        }
        if (!permitted) throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_REVIEW_NOT_ALLOWED", "Review decision is not allowed for this status.");
        if ("DISCARD".equals(decision)) reviewCommit.discard(jobId, expected, adminId, reason, tags);
    }

    private AutoRegisterVerifiedContentUseCase.VerifiedContentCommand registrationCommand(AiExtractionAdminQueryPort.ReviewTarget target) {
        JsonNode fields = target.candidateFields();
        JsonNode evidence = target.evidence();
        VerifyAiContentCandidateUseCase.VerificationResult verification = verifier.verify(
                new VerifyAiContentCandidateUseCase.VerificationCommand(
                        target.channelId(), target.videoId(), URI.create(target.videoUrl()),
                        text(fields, "restaurantName"), text(fields, "address"),
                        URI.create(text(fields, "location")), text(fields, "menu"),
                        visitEvidence(fields, target.fieldConfidences(), evidence)));
        if (!verification.isVerified()) {
            throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_REVIEW_REVALIDATION_FAILED", verification.failureReason());
        }
        VerifyAiContentCandidateUseCase.VerifiedContent verified = verification.content();
        return new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                        verified.regionId(), verified.foodCategoryId(), verified.restaurantName(), verified.kakaoPlaceId(),
                        verified.kakaoPlaceUrl(), verified.roadAddress(), null, verified.phoneNumber(), verified.latitude(), verified.longitude()),
                new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                        verified.channelId(), verified.channelName(), verified.channelUrl()),
                new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                        verified.videoId(), verified.channelId(), verified.videoTitle(), verified.videoSourceUrl(),
                        verified.videoThumbnailUrl(), verified.publishedAt(), verified.checkedAt()), true);
    }

    private VerifyAiContentCandidateUseCase.VisitEvidenceCandidate visitEvidence(JsonNode fields, JsonNode confidences, JsonNode evidence) {
        JsonNode visit = evidence == null ? null : evidence.get("visitEvidence");
        if (visit == null) return null;
        VerifyAiContentCandidateUseCase.EvidenceType type = switch (visit.path("type").asText()) {
            case "TIMESTAMP" -> VerifyAiContentCandidateUseCase.EvidenceType.TIMESTAMP;
            case "TEXT_RANGE" -> VerifyAiContentCandidateUseCase.EvidenceType.TEXT_RANGE;
            default -> VerifyAiContentCandidateUseCase.EvidenceType.UNKNOWN;
        };
        String sourceHash = visit.path("sourceHash").asText(null);
        VerifyAiContentCandidateUseCase.Evidence location = new VerifyAiContentCandidateUseCase.Evidence(type,
                longValue(visit, "startMs"), longValue(visit, "endMs"), longValue(visit, "startOffset"),
                longValue(visit, "endOffset"), sourceHash);
        return new VerifyAiContentCandidateUseCase.VisitEvidenceCandidate(text(fields, "visitEvidence"),
                confidence(confidences, "visitEvidence"), location);
    }

    private String text(JsonNode node, String name) {
        String value = node == null ? null : node.path(name).asText(null);
        if (value == null || value.isBlank()) throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_REVIEW_REVALIDATION_FAILED", "The candidate is missing required verification data.");
        return value;
    }

    private Long longValue(JsonNode node, String name) { return node != null && node.has(name) ? node.path(name).asLong() : null; }
    private double confidence(JsonNode fields, String name) { return fields != null && fields.has(name) ? fields.path(name).asDouble(-1) : -1; }

    private void validateTagDecisions(AiExtractionAdminQueryPort.ReviewTarget target,
                                      List<AiExtractionAdminQueryPort.TagDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) return;
        Set<String> candidateIds = new HashSet<>();
        JsonNode candidates = target.candidateTags();
        if (candidates != null && candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                String id = candidate.path("candidateTagId").asText(null);
                if (id != null) candidateIds.add(id);
            }
        }
        for (AiExtractionAdminQueryPort.TagDecision decision : decisions) {
            if (decision == null || decision.candidateTagId() == null
                    || !candidateIds.contains(decision.candidateTagId())
                    || !"MANUAL_OVERRIDE".equals(decision.decision())
                    || (decision.tagCode() != null && (decision.tagCode().isBlank() || decision.tagCode().trim().length() > 64))) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "tagDecisions", "Tag decisions must reference Snapshot candidates and use MANUAL_OVERRIDE.");
            }
        }
    }
}
