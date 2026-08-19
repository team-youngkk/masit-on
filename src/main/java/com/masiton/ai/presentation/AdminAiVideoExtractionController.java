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
import com.masiton.ai.application.RegistrationUnitCommandService;
import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin/ai/video-extractions")
public class AdminAiVideoExtractionController {

    private static final Set<String> EXECUTION_STATUSES = Set.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED");
    private static final Set<String> SOURCES = Set.of("WEBHOOK", "ADMIN");
    private static final Set<String> REVIEW_STATUSES = Set.of("AUTO_CONFIRMED", "AUTO_BLOCKED", "AUTO_REJECTED", "MANUAL_OVERRIDE");

    private final AiExtractionJobUseCase useCase;
    private final AdminAiExtractionQueryService queryService;
    private final ObjectMapper objectMapper;

    public AdminAiVideoExtractionController(
            AiExtractionJobUseCase useCase,
            AdminAiExtractionQueryService queryService,
            ObjectMapper objectMapper
    ) {
        this.useCase = useCase;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
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
    public DetailResponse detail(@PathVariable UUID jobId) {
        return DetailResponse.from(queryService.detail(jobId), objectMapper);
    }
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
        String kakaoPlaceUrl = request.supplements() == null ? null : request.supplements().kakaoPlaceUrl();
        String foodCategoryId = request.supplements() == null ? null : request.supplements().foodCategoryId();
        queryService.review(jobId, request.decision(), request.unitId(), request.reason(), kakaoPlaceUrl, foodCategoryId,
                request.tagDecisions() == null ? List.of() : request.tagDecisions().stream()
                        .map(t -> new AiExtractionAdminQueryPort.TagDecision(t.candidateTagId(), t.decision(), t.tagCode()))
                        .toList(),
                adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{jobId}/registration-units/{unitId}/registration")
    public ResponseEntity<RegistrationExecutionResponse> registerUnit(@PathVariable UUID jobId, @PathVariable UUID unitId) {
        RegistrationUnitCommandService.RegistrationExecutionView result = queryService.registerUnit(jobId, unitId);
        return ResponseEntity.ok(RegistrationExecutionResponse.from(result));
    }

    /**
     * {@code member_account.id}(JWT subject)를 그대로 반환한다. {@code RegistrationUnitCommandService}가
     * 이 값을 {@code ai_registration_unit_review.reviewed_by}(member_account FK)에 그대로 저장하고,
     * legacy {@code admin_account} FK가 필요한 곳에서만 {@code LegacyAdminActorResolver}로 변환하므로
     * 여기서 미리 변환하면 안 된다.
     */
    private UUID adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

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
    public record ReviewRequest(String decision, String reason, String expectedReviewStatus, String unitId,
                                SupplementsRequest supplements, List<TagDecisionRequest> tagDecisions) { }
    public record SupplementsRequest(String kakaoPlaceUrl, String foodCategoryId) { }
    public record TagDecisionRequest(String candidateTagId, String decision, String tagCode) { }
    public record PageResponse(List<AiExtractionJobResponse> items, Page page) { }
    public record Page(int number, int size, long totalElements, long totalPages, boolean hasNext) { }

    public record RegistrationExecutionResponse(
            UUID unitId, String reviewStatus, UUID restaurantId, UUID creatorId, UUID videoId, UUID visitId,
            List<String> reusedResources, JsonNode placeDecision, JsonNode categoryDecision) {
        static RegistrationExecutionResponse from(RegistrationUnitCommandService.RegistrationExecutionView view) {
            return new RegistrationExecutionResponse(view.unitId(), view.reviewStatus(), view.restaurantId(),
                    view.creatorId(), view.videoId(), view.visitId(), view.reusedResources(), view.placeDecision(),
                    view.categoryDecision());
        }
    }

    public record RegistrationUnitResponse(
            UUID unitId, String restaurantName, String reviewStatus, String manualOverrideType, String blockReason,
            UUID registeredRestaurantId, UUID registeredCreatorId, UUID registeredVideoId, UUID registeredVisitId,
            List<String> reusedResources, JsonNode placeDecision, JsonNode categoryDecision) {

        static RegistrationUnitResponse from(AiRegistrationUnitStore.RegistrationUnitRow row, ObjectMapper mapper) {
            return new RegistrationUnitResponse(row.id(), row.restaurantName(), row.reviewStatus(),
                    row.manualOverrideType(), row.blockReason(), row.registeredRestaurantId(),
                    row.registeredCreatorId(), row.registeredVideoId(), row.registeredVisitId(),
                    row.reusedResources(), readTree(mapper, row.placeDecisionJson()),
                    readTree(mapper, row.categoryDecisionJson()));
        }

        private static JsonNode readTree(ObjectMapper mapper, String json) {
            return json == null || json.isBlank() ? null : mapper.readTree(json);
        }
    }

    public record DetailResponse(
            UUID jobId, String source, YoutubeReference youtube, String executionStatus, String resultCompleteness,
            String reviewStatus, String provider, String modelVersion, String promptVersion, String schemaVersion,
            int attemptCount, java.time.OffsetDateTime createdAt, java.time.OffsetDateTime startedAt,
            java.time.OffsetDateTime finishedAt, boolean reused, List<Object> candidates, Object missingFields,
            boolean candidateTruncated, List<RegistrationUnitResponse> registrationUnits, Error error,
            List<AiExtractionAdminQueryPort.Attempt> attempts) {
        static DetailResponse from(AdminAiExtractionQueryService.AdminJobDetail jobDetail, ObjectMapper objectMapper) {
            AiExtractionAdminQueryPort.Detail d = jobDetail.detail();
            AiExtractionJobResponse job = AiExtractionJobResponse.from(d.job());
            List<RegistrationUnitResponse> registrationUnits = jobDetail.registrationUnits().stream()
                    .map(row -> RegistrationUnitResponse.from(row, objectMapper))
                    .toList();
            return new DetailResponse(job.jobId(), job.source(), job.youtube(), job.executionStatus(),
                    job.resultCompleteness(), jobDetail.topReviewStatus(), job.provider(), job.modelVersion(),
                    job.promptVersion(), job.schemaVersion(), job.attemptCount(), job.createdAt(), job.startedAt(),
                    job.finishedAt(), job.reused(), candidates(d), d.missingFields(), d.candidateTruncated(),
                    registrationUnits,
                    d.errorCategory() == null ? null : new Error(d.errorCategory(), d.retryable(), job.attemptCount()),
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
