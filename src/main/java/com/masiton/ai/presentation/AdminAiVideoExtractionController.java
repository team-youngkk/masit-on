package com.masiton.ai.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.AdminAiExtractionQueryService;
import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/ai/video-extractions")
public class AdminAiVideoExtractionController {

    private static final Set<String> EXECUTION_STATUSES = Set.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED");
    private static final Set<String> SOURCES = Set.of("WEBHOOK", "ADMIN");
    private static final Set<String> REVIEW_STATUSES = Set.of("AUTO_CONFIRMED", "AUTO_BLOCKED", "AUTO_REJECTED", "MANUAL_OVERRIDE");

    private final AiExtractionJobUseCase useCase;
    private final AdminAiExtractionQueryService queryService;

    public AdminAiVideoExtractionController(AiExtractionJobUseCase useCase, AdminAiExtractionQueryService queryService) {
        this.useCase = useCase; this.queryService = queryService;
    }

    @GetMapping
    public PageResponse list(@RequestParam(required = false) String executionStatus, @RequestParam(required = false) String source,
                             @RequestParam(required = false) String reviewStatus, @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "Invalid page request.");
        if (size != 10 && size != 20 && size != 50) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "Invalid page size.");
        validateFilter(executionStatus, EXECUTION_STATUSES, "executionStatus");
        validateFilter(source, SOURCES, "source");
        validateFilter(reviewStatus, REVIEW_STATUSES, "reviewStatus");
        AiExtractionAdminQueryPort.Page result = queryService.list(executionStatus, source, reviewStatus, page, size);
        long totalPages = (result.totalElements() + size - 1) / size;
        return new PageResponse(result.items().stream().map(AiExtractionJobResponse::from).toList(), new Page(page, size, result.totalElements(), totalPages, page < totalPages));
    }
    private void validateFilter(String value, Set<String> allowed, String field) {
        if (value != null && !allowed.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "Invalid filter value.");
        }
    }
    @GetMapping("/{jobId}")
    public DetailResponse detail(@PathVariable UUID jobId) { return DetailResponse.from(queryService.detail(jobId)); }
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<AiExtractionJobResponse> retry(@PathVariable UUID jobId, @RequestBody RetryRequest request) {
        String url = queryService.retryUrl(jobId);
        if (request.supplementText() == null || request.supplementText().isBlank()) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "supplementText", "supplementText is required.");
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 1_000) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "reason", "reason is required and must be at most 1,000 characters.");
        // This is deliberately a fresh submit: no temporary input from the original job is read.
        return ResponseEntity.accepted().body(AiExtractionJobResponse.from(useCase.submitRetry(url, request.supplementText(), request.reason())));
    }
    @PostMapping("/{jobId}/review")
    public ResponseEntity<Void> review(Authentication authentication, @PathVariable UUID jobId, @RequestBody ReviewRequest request) {
        queryService.review(jobId, request.decision(), request.expectedReviewStatus(), adminId(authentication), request.reason(),
                request.tagDecisions() == null ? List.of() : request.tagDecisions().stream().map(t -> new AiExtractionAdminQueryPort.TagDecision(t.candidateTagId(), t.decision(), t.tagCode())).toList());
        return ResponseEntity.noContent().build();
    }
    private UUID adminId(Authentication authentication) { try { return UUID.fromString(authentication.getName()); } catch (Exception e) { throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED); } }

    @PostMapping
    public ResponseEntity<AiExtractionJobResponse> submit(@RequestBody SubmitRequest request) {
        if (request.videoUrl() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "videoUrl", "videoUrl is required.");
        }
        AiExtractionJobView view = useCase.submitAdmin(request.videoUrl(), request.supplementText(), request.idempotencyKey());
        return ResponseEntity.status(view.reused() ? 200 : 202).body(AiExtractionJobResponse.from(view));
    }

    public record SubmitRequest(
            String videoUrl,
            String supplementText,
            String idempotencyKey
    ) {
    }

    public record AiExtractionJobResponse(
            java.util.UUID jobId,
            String source,
            YoutubeReference youtube,
            String executionStatus,
            String resultCompleteness,
            String reviewStatus,
            String provider,
            String modelVersion,
            String promptVersion,
            String schemaVersion,
            int attemptCount,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime startedAt,
            java.time.OffsetDateTime finishedAt,
            boolean reused
    ) {
        static AiExtractionJobResponse from(AiExtractionJobView view) {
            return new AiExtractionJobResponse(view.jobId(), view.source(),
                    new YoutubeReference(view.channelId(), view.videoId(), view.videoUrl()),
                    view.executionStatus(), view.resultCompleteness(), view.reviewStatus(), view.provider(),
                    view.modelVersion(), view.promptVersion(), view.schemaVersion(), view.attemptCount(),
                    view.createdAt(), view.startedAt(), view.finishedAt(), view.reused());
        }
    }

    public record YoutubeReference(String channelId, String videoId, String videoUrl) {
    }
    public record RetryRequest(String supplementText, String reason) { }
    public record ReviewRequest(String decision, String reason, String expectedReviewStatus, List<TagDecisionRequest> tagDecisions) { }
    public record TagDecisionRequest(String candidateTagId, String decision, String tagCode) { }
    public record PageResponse(List<AiExtractionJobResponse> items, Page page) { }
    public record Page(int number, int size, long totalElements, long totalPages, boolean hasNext) { }
    public record DetailResponse(
            UUID jobId, String source, YoutubeReference youtube, String executionStatus, String resultCompleteness,
            String reviewStatus, String provider, String modelVersion, String promptVersion, String schemaVersion,
            int attemptCount, java.time.OffsetDateTime createdAt, java.time.OffsetDateTime startedAt,
            java.time.OffsetDateTime finishedAt, boolean reused, List<Object> candidates, Object missingFields, Error error,
            List<AiExtractionAdminQueryPort.Attempt> attempts) {
        static DetailResponse from(AiExtractionAdminQueryPort.Detail d) {
            AiExtractionJobResponse job = AiExtractionJobResponse.from(d.job());
            return new DetailResponse(job.jobId(), job.source(), job.youtube(), job.executionStatus(), job.resultCompleteness(),
                    job.reviewStatus(), job.provider(), job.modelVersion(), job.promptVersion(), job.schemaVersion(),
                    job.attemptCount(), job.createdAt(), job.startedAt(), job.finishedAt(), job.reused(),
                    candidates(d), d.missingFields(), d.errorCategory() == null ? null : new Error(d.errorCategory(), d.retryable(), job.attemptCount()),
                    d.attempts());
        }

        private static List<Object> candidates(AiExtractionAdminQueryPort.Detail d) {
            List<Object> values = new java.util.ArrayList<>();
            if (d.candidateFields() != null && d.candidateFields().isObject()) {
                d.candidateFields().properties().forEach(entry -> {
                    JsonNode fieldValue = entry.getValue();
                    if (fieldValue.isArray()) {
                        // Multiple candidates for the same field (BR-AIEXTRACT-001) are preserved as a list.
                        fieldValue.forEach(item -> values.add(candidateOf(entry.getKey(), item.path("value").asText(),
                                item.path("confidence").asDouble(0), item.path("evidence"))));
                        return;
                    }
                    values.add(candidateOf(entry.getKey(), fieldValue.asText(),
                            d.fieldConfidences().path(entry.getKey()).asDouble(0), d.evidence().path(entry.getKey())));
                });
            }
            if (d.candidateTags() != null && d.candidateTags().isArray()) {
                d.candidateTags().forEach(tag -> values.add(tag));
            }
            return values;
        }

        private static java.util.Map<String, Object> candidateOf(String field, String value, double confidence,
                                                                   JsonNode evidence) {
            java.util.Map<String, Object> candidate = new java.util.LinkedHashMap<>();
            candidate.put("field", field);
            candidate.put("value", value);
            candidate.put("confidence", confidence);
            candidate.put("evidence", evidence);
            return candidate;
        }
    }
    public record Error(String category, Boolean retryable, int attemptCount) { }
}
