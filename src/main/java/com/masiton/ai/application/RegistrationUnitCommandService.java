package com.masiton.ai.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitConcurrentAccessException;
import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.common.security.LegacyAdminActorResolver;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.orchestration.application.port.in.AdjustRegisteredCategoryUseCase;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.RegistrationUnitBundle;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionCommand;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionResult;
import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase;
import com.masiton.orchestration.application.port.in.RollbackAiRegisteredContentUseCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code BR-AIEXTRACT-011} 등록 단위 granularity의 등록 실행(API 3.6절)과 사후 보정·롤백
 * (API 3.5절 {@code review})을 소유한다. Worker와 같은 판정 규칙을 재사용하기 위해
 * {@link ExecuteRegistrationUnitUseCase}를 그대로 호출하고, 등록 단위 상태 전이·감사 이력만
 * 이 서비스가 담당한다.
 *
 * <p>외부 조회(Kakao·YouTube)가 필요한 경로({@link #registerUnit}, {@code CONFIRM})는 이 클래스의
 * 메서드 전체를 트랜잭션으로 감싸지 않는다. 대신 외부 호출이 끝난 뒤의 최종 상태 전이를
 * {@code expectedReviewStatus} 조건부 갱신({@link AiRegistrationUnitStore#markRegistered}·
 * {@link AiRegistrationUnitStore#confirmWithSupplement})으로 원자화해, 같은 등록 단위에 대한
 * 동시 요청 중 하나만 반영되고 나머지는 {@code AIEXTRACT_CONCURRENT_REQUEST_CONFLICT}로 거절되게
 * 한다. {@code CONFIRM}의 등록 단위 상태·태그 연결·감사 이력 세 쓰기는
 * {@link RegistrationUnitConfirmCommitService}가 하나의 트랜잭션으로 묶어 커밋한다.</p>
 */
@Service
public class RegistrationUnitCommandService {

    private static final Set<String> KNOWN_DECISIONS = Set.of("CONFIRM", "DISCARD", "ROLLBACK", "ADJUST_CATEGORY");
    private static final String ADMIN_EXECUTOR = "ADMIN";

    private final AiExtractionAdminQueryPort port;
    private final AiRegistrationUnitStore registrationUnitStore;
    private final AiRegistrationUnitReviewStore registrationUnitReviewStore;
    private final DecomposeRegistrationUnitsUseCase decomposeRegistrationUnits;
    private final ExecuteRegistrationUnitUseCase executeRegistrationUnit;
    private final ResolveFoodCategoryUseCase resolveFoodCategory;
    private final RollbackAiRegisteredContentUseCase rollbackUseCase;
    private final AdjustRegisteredCategoryUseCase adjustCategoryUseCase;
    private final RegistrationUnitConfirmCommitService confirmCommitService;
    private final LegacyAdminActorResolver legacyAdminActorResolver;
    private final ObjectMapper objectMapper;

    public RegistrationUnitCommandService(
            AiExtractionAdminQueryPort port,
            AiRegistrationUnitStore registrationUnitStore,
            AiRegistrationUnitReviewStore registrationUnitReviewStore,
            DecomposeRegistrationUnitsUseCase decomposeRegistrationUnits,
            ExecuteRegistrationUnitUseCase executeRegistrationUnit,
            ResolveFoodCategoryUseCase resolveFoodCategory,
            RollbackAiRegisteredContentUseCase rollbackUseCase,
            AdjustRegisteredCategoryUseCase adjustCategoryUseCase,
            RegistrationUnitConfirmCommitService confirmCommitService,
            LegacyAdminActorResolver legacyAdminActorResolver,
            ObjectMapper objectMapper) {
        this.port = port;
        this.registrationUnitStore = registrationUnitStore;
        this.registrationUnitReviewStore = registrationUnitReviewStore;
        this.decomposeRegistrationUnits = decomposeRegistrationUnits;
        this.executeRegistrationUnit = executeRegistrationUnit;
        this.resolveFoodCategory = resolveFoodCategory;
        this.rollbackUseCase = rollbackUseCase;
        this.adjustCategoryUseCase = adjustCategoryUseCase;
        this.confirmCommitService = confirmCommitService;
        this.legacyAdminActorResolver = legacyAdminActorResolver;
        this.objectMapper = objectMapper;
    }

    // =========================================================================================
    // API 3.6 등록 단위 일괄 등록
    // =========================================================================================

    public RegistrationExecutionView registerUnit(UUID jobId, UUID unitId) {
        AiExtractionAdminQueryPort.JobVideoReference jobRef = requireJob(jobId);
        AiRegistrationUnitStore.RegistrationUnitRow unit = lockUnit(jobId, unitId);
        return switch (unit.reviewStatus()) {
            case "AUTO_BLOCKED" -> executeAndPersist(jobRef, unit);
            case "AUTO_CONFIRMED" -> RegistrationExecutionView.fromRow(unit, objectMapper);
            case "MANUAL_OVERRIDE" -> {
                if (unit.isRegistered()) {
                    yield RegistrationExecutionView.fromRow(unit, objectMapper);
                }
                throw validationConflict(null);
            }
            default -> throw validationConflict(null);
        };
    }

    private RegistrationExecutionView executeAndPersist(AiExtractionAdminQueryPort.JobVideoReference jobRef,
                                                         AiRegistrationUnitStore.RegistrationUnitRow unit) {
        RegistrationUnitBundle bundle = bundleFor(unit);
        RegistrationUnitExecutionCommand command = buildCommand(bundle, jobRef, null, null, null);
        RegistrationUnitExecutionResult result = executeRegistrationUnit.execute(command);
        if (!result.confirmed()) {
            throw validationConflict(result.blockReason());
        }
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = result.registration();
        AiRegistrationUnitStore.RegisteredResult registered = new AiRegistrationUnitStore.RegisteredResult(
                registration.restaurantId(), registration.creatorId(), registration.videoId(), registration.visitId(),
                RegistrationUnitJsonSupport.reusedResourcesJson(objectMapper, registration),
                RegistrationUnitJsonSupport.placeDecisionJson(objectMapper, result.placeDecision()),
                RegistrationUnitJsonSupport.categoryDecisionJson(objectMapper, result.categoryDecision()),
                ADMIN_EXECUTOR);
        boolean updated;
        try {
            updated = registrationUnitStore.markRegistered(unit.id(), "AUTO_BLOCKED", registered);
        } catch (RuntimeException exception) {
            compensateFailedRegistration(registration);
            throw exception instanceof DataIntegrityViolationException ? concurrentConflict() : exception;
        }
        if (!updated) {
            compensateFailedRegistration(registration);
            throw concurrentConflict();
        }
        return RegistrationExecutionView.fromRegistration(unit.id(), "AUTO_CONFIRMED", registration, registered);
    }

    // =========================================================================================
    // API 3.7 등록 단위 일괄 폐기
    // =========================================================================================

    /**
     * 작업의 {@code AUTO_BLOCKED} 등록 단위를 모두 폐기한다. 등록 단위 사이에는 교차 원자성이
     * 필요 없으므로({@code AUTO_BLOCKED} 각각은 독립적으로 검토된다) 하나씩 다시 잠그고 여전히
     * {@code AUTO_BLOCKED}인 것만 {@link #discard}로 폐기한다. 동시 요청으로 이미 상태가 바뀐
     * 단위는(잠금 실패, 재확인 시 상태 불일치 포함) 조용히 건너뛰고 나머지를 계속 처리해 반복
     * 호출에도 안전한 부분 성공을 허용한다. 개별 단위 처리 중 예상치 못한 DB 오류가 나도 이미
     * 폐기에 성공한 단위를 잃지 않도록, 그 단위만 건너뛰고 나머지를 계속 처리한다.
     */
    public List<UUID> discardAllBlocked(UUID jobId, String reason, UUID adminId) {
        requireReason("DISCARD", reason);
        String trimmedReason = reason.trim();
        requireJob(jobId);

        List<AiRegistrationUnitStore.RegistrationUnitRow> units = registrationUnitStore.findByJobId(jobId);
        List<UUID> discardedUnitIds = new java.util.ArrayList<>();
        for (AiRegistrationUnitStore.RegistrationUnitRow candidate : units) {
            if (!"AUTO_BLOCKED".equals(candidate.reviewStatus())) {
                continue;
            }
            AiRegistrationUnitStore.RegistrationUnitRow locked;
            try {
                locked = registrationUnitStore.lockByJobAndUnitId(jobId, candidate.id()).orElse(null);
            } catch (AiRegistrationUnitConcurrentAccessException exception) {
                continue;
            }
            if (locked == null || !"AUTO_BLOCKED".equals(locked.reviewStatus())) {
                continue;
            }
            try {
                discard(locked, trimmedReason, adminId);
            } catch (RuntimeException exception) {
                continue;
            }
            discardedUnitIds.add(locked.id());
        }
        return List.copyOf(discardedUnitIds);
    }

    // =========================================================================================
    // API 3.5 review
    // =========================================================================================

    /**
     * {@code CONFIRM}은 외부 조회(Kakao)를 포함할 수 있으므로 이 메서드 전체를 {@code @Transactional}로
     * 감싸지 않는다. 네 결정 모두 {@code lockUnit}으로 읽은 시점의 상태를 {@code expectedReviewStatus}로
     * 넘겨, 최종 저장이 {@code WHERE review_status = expectedReviewStatus}(등록 결과가 있어야 하는
     * {@code ROLLBACK}·{@code ADJUST_CATEGORY}는 {@code registered_restaurant_id IS NOT NULL}도 함께)
     * 조건을 만족할 때만 반영되게 한다. 조건이 어긋나면(동시 요청이 먼저 반영) 아무것도 바꾸지 않고
     * {@code false}를 반환하며 이 클래스가 {@code concurrentConflict()}로 응답한다. {@code ROLLBACK}·
     * {@code ADJUST_CATEGORY}의 등록 콘텐츠 반영은 {@link RollbackAiRegisteredContentUseCase}·
     * {@link AdjustRegisteredCategoryUseCase}가 각자 소유한 트랜잭션에서 먼저 수행한 뒤 등록 단위
     * 상태를 반영한다.
     */
    public void review(UUID jobId, String decision, String unitIdRaw, String reason, String suppliedKakaoPlaceUrl,
                       String suppliedFoodCategoryIdRaw, List<AiExtractionAdminQueryPort.TagDecision> tagDecisions,
                       UUID adminId) {
        requireDecision(decision);
        requireReason(decision, reason);
        String trimmedReason = reason == null ? null : reason.trim();
        List<AiExtractionAdminQueryPort.TagDecision> decisions = tagDecisions == null ? List.of() : tagDecisions;

        AiExtractionAdminQueryPort.JobVideoReference jobRef = requireJob(jobId);
        UUID unitId = resolveUnitId(jobId, unitIdRaw);
        AiRegistrationUnitStore.RegistrationUnitRow unit = lockUnit(jobId, unitId);

        switch (decision) {
            case "CONFIRM" -> confirm(unit, jobRef, trimmedReason, suppliedKakaoPlaceUrl, suppliedFoodCategoryIdRaw,
                    decisions, adminId);
            case "DISCARD" -> discard(unit, trimmedReason, adminId);
            case "ROLLBACK" -> rollback(unit, trimmedReason, adminId);
            case "ADJUST_CATEGORY" -> adjustCategory(unit, trimmedReason, suppliedFoodCategoryIdRaw, adminId);
            default -> throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "decision", "Unsupported decision.");
        }
    }

    private void confirm(AiRegistrationUnitStore.RegistrationUnitRow unit,
                         AiExtractionAdminQueryPort.JobVideoReference jobRef, String reason,
                         String suppliedKakaoPlaceUrl, String suppliedFoodCategoryIdRaw,
                         List<AiExtractionAdminQueryPort.TagDecision> tagDecisions, UUID adminId) {
        if (!"AUTO_BLOCKED".equals(unit.reviewStatus())) {
            throw validationConflict(unit.blockReason());
        }
        // Validate tagDecisions up front: nothing below this point may commit if the request is invalid,
        // otherwise a validation failure after confirmWithSupplement would leave the unit registered with
        // no audit row and no recoverable retry path (its blockReason is gone once it is no longer AUTO_BLOCKED).
        if (!tagDecisions.isEmpty()) {
            validateTagDecisions(unit, tagDecisions);
        }
        String blockReason = unit.blockReason();
        UUID suppliedFoodCategoryId = null;
        String suppliedFoodCategoryName = null;
        String submittedSupplementsJson;

        switch (blockReason == null ? "" : blockReason) {
            case "PLACE_NOT_FOUND", "PLACE_AMBIGUOUS" -> {
                requireOnlyKakaoPlaceUrl(suppliedKakaoPlaceUrl, suppliedFoodCategoryIdRaw);
                submittedSupplementsJson = supplementJson("kakaoPlaceUrl", suppliedKakaoPlaceUrl);
            }
            case "CATEGORY_UNRESOLVED" -> {
                requireOnlyFoodCategoryId(suppliedKakaoPlaceUrl, suppliedFoodCategoryIdRaw);
                UUID categoryId = parseUuid(suppliedFoodCategoryIdRaw, "supplements.foodCategoryId");
                String categoryName = resolveFoodCategory.findActiveCategoryName(categoryId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                                "supplements.foodCategoryId", "foodCategoryId must reference an active food category."));
                suppliedFoodCategoryId = categoryId;
                suppliedFoodCategoryName = categoryName;
                submittedSupplementsJson = supplementJson("foodCategoryId", suppliedFoodCategoryIdRaw);
                suppliedKakaoPlaceUrl = null;
            }
            default -> throw validationConflict(blockReason);
        }

        RegistrationUnitBundle bundle = bundleFor(unit);
        RegistrationUnitExecutionCommand command = buildCommand(bundle, jobRef, suppliedKakaoPlaceUrl,
                suppliedFoodCategoryId, suppliedFoodCategoryName);
        RegistrationUnitExecutionResult result = executeRegistrationUnit.execute(command);
        if (!result.confirmed()) {
            // BR-AIEXTRACT-011/API 3.5: supplement validation failure keeps the original blockReason
            // and persists nothing.
            throw validationConflict(unit.blockReason());
        }

        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = result.registration();
        AiRegistrationUnitStore.RegisteredResult registered = new AiRegistrationUnitStore.RegisteredResult(
                registration.restaurantId(), registration.creatorId(), registration.videoId(), registration.visitId(),
                RegistrationUnitJsonSupport.reusedResourcesJson(objectMapper, registration),
                RegistrationUnitJsonSupport.placeDecisionJson(objectMapper, result.placeDecision()),
                RegistrationUnitJsonSupport.categoryDecisionJson(objectMapper, result.categoryDecision()), null);
        boolean committed;
        try {
            committed = confirmCommitService.commit(unit.id(), "AUTO_BLOCKED", registered, unit.snapshotId(),
                    registration.visitId(), tagDecisions, adminId, reason, submittedSupplementsJson);
        } catch (RuntimeException exception) {
            compensateFailedRegistration(registration);
            throw exception instanceof DataIntegrityViolationException ? concurrentConflict() : exception;
        }
        if (!committed) {
            compensateFailedRegistration(registration);
            throw concurrentConflict();
        }
    }

    /**
     * {@code AutoRegisterVerifiedContentUseCase#register}는 자신의 트랜잭션에서 즉시 커밋되므로,
     * 그 뒤 등록 단위 상태 반영({@code markRegistered}·{@link RegistrationUnitConfirmCommitService#commit})이
     * 실패(동시 요청 선점, unique 제약 위반, 태그·감사 저장 중 예외 등 어떤 이유든)하면 방금 만든 4종
     * 자원이 어떤 등록 단위에도 연결되지 못한 채 남는다. 맛집은 재사용 대상이 아니므로(이 등록 단위는
     * 항상 새 맛집·방문을 만든다) 이 상태를 그대로 두면 같은 {@code kakaoPlaceId}의 재시도가
     * {@code DUPLICATE_CONFLICT}로 영구히 막힌다.
     *
     * <p>{@code review}의 {@code ROLLBACK}이 쓰는 {@link RollbackAiRegisteredContentUseCase#rollback}은
     * 감사 보존을 위해 {@code publication_status}만 {@code PRIVATE}로 바꿀 뿐 행을 지우지 않으므로,
     * {@code kakao_place_id} unique 제약이 남아 재시도를 막는다. 여기서는 유효하게 등록된 적 없는
     * 데이터이므로 {@link RollbackAiRegisteredContentUseCase#discardFailedRegistration}로 하드
     * 삭제해야 재시도가 실제로 열린다.</p>
     */
    private void compensateFailedRegistration(AutoRegisterVerifiedContentUseCase.RegistrationResult registration) {
        rollbackUseCase.discardFailedRegistration(registration.restaurantId(), registration.restaurantCreated(),
                registration.creatorId(), registration.creatorCreated(), registration.videoId(),
                registration.videoCreated(), registration.visitId(), registration.visitCreated());
    }

    private void discard(AiRegistrationUnitStore.RegistrationUnitRow unit, String reason, UUID adminId) {
        if (!"AUTO_BLOCKED".equals(unit.reviewStatus())) {
            throw validationConflict(unit.blockReason());
        }
        if (!registrationUnitStore.discard(unit.id(), "AUTO_BLOCKED", OffsetDateTime.now())) {
            throw concurrentConflict();
        }
        registrationUnitReviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unit.id(), "DISCARD", reason, null, null, null, adminId));
    }

    private void rollback(AiRegistrationUnitStore.RegistrationUnitRow unit, String reason, UUID adminId) {
        if (!isRegisteredEligibleForManualOverride(unit)) {
            throw validationConflict(null);
        }
        ObjectNode reverted = objectMapper.createObjectNode();
        reverted.put("restaurantId", unit.registeredRestaurantId().toString());
        reverted.put("creatorId", unit.registeredCreatorId().toString());
        reverted.put("videoId", unit.registeredVideoId().toString());
        reverted.put("visitId", unit.registeredVisitId().toString());

        rollbackUseCase.rollback(new RollbackAiRegisteredContentUseCase.RegistrationReference(
                unit.snapshotId(), unit.registeredRestaurantId(), true, unit.registeredCreatorId(),
                !unit.reusedResources().contains("creator"), unit.registeredVideoId(),
                !unit.reusedResources().contains("video"), unit.registeredVisitId(), true));
        if (!registrationUnitStore.rollback(unit.id(), unit.reviewStatus(), OffsetDateTime.now())) {
            throw concurrentConflict();
        }
        registrationUnitReviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unit.id(), "ROLLBACK", reason, null, null, writeJson(reverted), adminId));
    }

    private void adjustCategory(AiRegistrationUnitStore.RegistrationUnitRow unit, String reason,
                                String suppliedFoodCategoryIdRaw, UUID adminId) {
        if (!isRegisteredEligibleForManualOverride(unit)) {
            throw validationConflict(null);
        }
        if (blank(suppliedFoodCategoryIdRaw)) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "supplements.foodCategoryId",
                    "foodCategoryId is required.");
        }
        UUID categoryId = parseUuid(suppliedFoodCategoryIdRaw, "supplements.foodCategoryId");
        String categoryName = resolveFoodCategory.findActiveCategoryName(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "supplements.foodCategoryId",
                        "foodCategoryId must reference an active food category."));
        ObjectNode newDecision = objectMapper.createObjectNode();
        newDecision.put("foodCategoryId", categoryId.toString());
        newDecision.put("foodCategoryName", categoryName);
        newDecision.put("resolvedBy", "MANUAL_OVERRIDE");

        adjustCategoryUseCase.adjust(unit.registeredRestaurantId(), categoryId);
        if (!registrationUnitStore.adjustCategory(unit.id(), unit.reviewStatus(), writeJson(newDecision))) {
            throw concurrentConflict();
        }
        registrationUnitReviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unit.id(), "ADJUST_CATEGORY", reason, null, unit.categoryDecisionJson(), null, adminId));
    }

    private boolean isRegisteredEligibleForManualOverride(AiRegistrationUnitStore.RegistrationUnitRow unit) {
        return "AUTO_CONFIRMED".equals(unit.reviewStatus())
                || ("MANUAL_OVERRIDE".equals(unit.reviewStatus()) && unit.isRegistered());
    }

    private void validateTagDecisions(AiRegistrationUnitStore.RegistrationUnitRow unit,
                                      List<AiExtractionAdminQueryPort.TagDecision> decisions) {
        AiExtractionAdminQueryPort.SnapshotCandidateJson snapshot = port.snapshotCandidates(unit.snapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Snapshot candidates missing for registration unit: " + unit.id()));
        Set<String> candidateIds = new HashSet<>();
        JsonNode candidates = snapshot.candidateTags();
        if (candidates != null && candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                String id = candidate.path("candidateTagId").asText(null);
                if (id != null) {
                    candidateIds.add(id);
                }
            }
        }
        for (AiExtractionAdminQueryPort.TagDecision decision : decisions) {
            if (decision == null || decision.candidateTagId() == null
                    || !candidateIds.contains(decision.candidateTagId())
                    || !"MANUAL_OVERRIDE".equals(decision.decision())
                    || (decision.tagCode() != null && (decision.tagCode().isBlank()
                    || decision.tagCode().trim().length() > 64))) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "tagDecisions",
                        "Tag decisions must reference Snapshot candidates and use MANUAL_OVERRIDE.");
            }
        }
    }

    // =========================================================================================
    // 공통 헬퍼
    // =========================================================================================

    private AiExtractionAdminQueryPort.JobVideoReference requireJob(UUID jobId) {
        return port.jobVideoReference(jobId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "AIEXTRACT_JOB_NOT_FOUND",
                        "The AI extraction job was not found."));
    }

    private AiRegistrationUnitStore.RegistrationUnitRow lockUnit(UUID jobId, UUID unitId) {
        try {
            return registrationUnitStore.lockByJobAndUnitId(jobId, unitId).orElseThrow(this::unitNotFound);
        } catch (AiRegistrationUnitConcurrentAccessException exception) {
            throw concurrentConflict();
        }
    }

    /**
     * {@code lockByJobAndUnitId}의 {@code FOR UPDATE NOWAIT}는 그 조회 문장이 끝나면 풀리므로,
     * Kakao·YouTube 외부 호출을 마친 뒤의 최종 상태 전이({@code markRegistered}·
     * {@code confirmWithSupplement}의 {@code expectedReviewStatus} 조건부 갱신, DB unique 제약)가
     * 실제 동시성 가드다. 둘 중 어느 쪽이 막았든 응답은 같다.
     */
    private BusinessException concurrentConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_CONCURRENT_REQUEST_CONFLICT",
                "Concurrent request on the same registration unit.");
    }

    private BusinessException unitNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "AIEXTRACT_UNIT_NOT_FOUND",
                "The registration unit was not found for this job.");
    }

    /** API 3.5절: 0개는 422, 1개는 생략 허용, 2개 이상은 {@code unitId} 필수. */
    private UUID resolveUnitId(UUID jobId, String unitIdRaw) {
        List<AiRegistrationUnitStore.RegistrationUnitRow> units = registrationUnitStore.findByJobId(jobId);
        UUID requested = unitIdRaw == null || unitIdRaw.isBlank() ? null : parseUuid(unitIdRaw, "unitId");
        if (units.isEmpty()) {
            throw validationConflict(null);
        }
        if (units.size() == 1) {
            UUID onlyId = units.get(0).id();
            if (requested != null && !requested.equals(onlyId)) {
                throw unitNotFound();
            }
            return onlyId;
        }
        if (requested == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AIEXTRACT_UNIT_ID_REQUIRED",
                    "unitId is required when the job has multiple registration units.");
        }
        boolean belongs = units.stream().anyMatch(row -> row.id().equals(requested));
        if (!belongs) {
            throw unitNotFound();
        }
        return requested;
    }

    private RegistrationUnitBundle bundleFor(AiRegistrationUnitStore.RegistrationUnitRow unit) {
        AiExtractionAdminQueryPort.SnapshotCandidateJson snapshot = port.snapshotCandidates(unit.snapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Snapshot candidates missing for registration unit: " + unit.id()));
        List<RegistrationUnitBundle> bundles = decomposeRegistrationUnits.decompose(
                new DecomposeRegistrationUnitsUseCase.DecomposeRegistrationUnitsCommand(
                        snapshot.candidateFields(), snapshot.fieldConfidences(), snapshot.evidence()));
        return bundles.stream().filter(bundle -> bundle.unitIndex() == unit.unitIndex()).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Registration unit bundle could not be re-derived: " + unit.id()));
    }

    private RegistrationUnitExecutionCommand buildCommand(RegistrationUnitBundle bundle,
                                                           AiExtractionAdminQueryPort.JobVideoReference jobRef,
                                                           String suppliedKakaoPlaceUrl, UUID suppliedFoodCategoryId,
                                                           String suppliedFoodCategoryName) {
        return new RegistrationUnitExecutionCommand(
                RegistrationUnitJsonSupport.value(bundle.restaurantName()),
                RegistrationUnitJsonSupport.value(bundle.address()), RegistrationUnitJsonSupport.value(bundle.menu()),
                RegistrationUnitJsonSupport.toVisitEvidence(bundle.visitEvidence()), jobRef.channelId(),
                jobRef.videoId(), URI.create(jobRef.videoUrl()), suppliedKakaoPlaceUrl, suppliedFoodCategoryId,
                suppliedFoodCategoryName);
    }

    private void requireDecision(String decision) {
        if (decision == null || !KNOWN_DECISIONS.contains(decision)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "decision", "decision must be one of "
                    + KNOWN_DECISIONS + ".");
        }
    }

    private void requireReason(String decision, String reason) {
        boolean reasonProvided = reason != null && !reason.isBlank();
        if ("ROLLBACK".equals(decision)) {
            if (reason != null && reason.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "reason", "reason must not be blank.");
            }
            // reason is only recommended for ROLLBACK, but the column is varchar(1000) regardless;
            // an over-length value must be rejected here, not surface as a DB error after side effects commit.
            if (reasonProvided && reason.trim().length() > 1_000) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "reason",
                        "reason must be at most 1,000 characters.");
            }
            return;
        }
        if (!reasonProvided || reason.trim().length() > 1_000) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "reason",
                    "reason is required and must be at most 1,000 characters.");
        }
    }

    private void requireOnlyKakaoPlaceUrl(String kakaoPlaceUrl, String foodCategoryId) {
        if (blank(kakaoPlaceUrl)) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "supplements.kakaoPlaceUrl",
                    "kakaoPlaceUrl is required.");
        }
        if (!blank(foodCategoryId)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "supplements.foodCategoryId",
                    "foodCategoryId must not be supplied for this blockReason.");
        }
    }

    private void requireOnlyFoodCategoryId(String kakaoPlaceUrl, String foodCategoryId) {
        if (blank(foodCategoryId)) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "supplements.foodCategoryId",
                    "foodCategoryId is required.");
        }
        if (!blank(kakaoPlaceUrl)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "supplements.kakaoPlaceUrl",
                    "kakaoPlaceUrl must not be supplied for this blockReason.");
        }
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "Invalid identifier format.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String supplementJson(String field, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(field, value);
        return writeJson(node);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("Registration unit review payload could not be serialized.", exception);
        }
    }

    private BusinessException validationConflict(String blockReason) {
        RecoveryMapping mapping = recoveryMappingFor(blockReason);
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "AIEXTRACT_VALIDATION_CONFLICT",
                "Registration unit validation conflict.",
                new ValidationConflictDetails(blockReason, mapping.recoveryPaths(), mapping.requiredSupplements()));
    }

    private RecoveryMapping recoveryMappingFor(String blockReason) {
        if (blockReason == null) {
            return new RecoveryMapping(List.of(), List.of());
        }
        return switch (blockReason) {
            case "PLACE_NOT_FOUND", "PLACE_AMBIGUOUS" ->
                    new RecoveryMapping(List.of("SUPPLEMENT", "MANUAL_REGISTRATION"), List.of("kakaoPlaceUrl"));
            case "CATEGORY_UNRESOLVED" ->
                    new RecoveryMapping(List.of("SUPPLEMENT", "MANUAL_REGISTRATION"), List.of("foodCategoryId"));
            case "MISSING_REQUIRED_FIELD", "VISIT_EVIDENCE_REQUIRED" ->
                    new RecoveryMapping(List.of("REEXTRACT", "MANUAL_REGISTRATION"), List.of());
            case "DUPLICATE_CONFLICT" -> new RecoveryMapping(List.of("EXISTING_RESOURCE"), List.of());
            case "EXTERNAL_SERVICE_ERROR" ->
                    new RecoveryMapping(List.of("RETRY", "MANUAL_REGISTRATION"), List.of());
            default -> new RecoveryMapping(List.of(), List.of());
        };
    }

    private record RecoveryMapping(List<String> recoveryPaths, List<String> requiredSupplements) {
    }

    /** {@code 422 AIEXTRACT_VALIDATION_CONFLICT} 응답의 {@code details} 페이로드다. */
    public record ValidationConflictDetails(String blockReason, List<String> recoveryPaths,
                                            List<String> requiredSupplements) {
    }

    /** API 3.6절 {@code 200 OK} 응답 페이로드다. */
    public record RegistrationExecutionView(
            UUID unitId, String reviewStatus, UUID restaurantId, UUID creatorId, UUID videoId, UUID visitId,
            List<String> reusedResources, JsonNode placeDecision, JsonNode categoryDecision) {

        static RegistrationExecutionView fromRow(AiRegistrationUnitStore.RegistrationUnitRow unit,
                                                 ObjectMapper objectMapper) {
            return new RegistrationExecutionView(unit.id(), unit.reviewStatus(), unit.registeredRestaurantId(),
                    unit.registeredCreatorId(), unit.registeredVideoId(), unit.registeredVisitId(),
                    unit.reusedResources(), readTree(objectMapper, unit.placeDecisionJson()),
                    readTree(objectMapper, unit.categoryDecisionJson()));
        }

        static RegistrationExecutionView fromRegistration(UUID unitId, String reviewStatus,
                AutoRegisterVerifiedContentUseCase.RegistrationResult registration,
                AiRegistrationUnitStore.RegisteredResult registered) {
            ObjectMapper mapper = new ObjectMapper();
            return new RegistrationExecutionView(unitId, reviewStatus, registration.restaurantId(),
                    registration.creatorId(), registration.videoId(), registration.visitId(),
                    reusedResourcesOf(registration), readTree(mapper, registered.placeDecisionJson()),
                    readTree(mapper, registered.categoryDecisionJson()));
        }

        private static List<String> reusedResourcesOf(AutoRegisterVerifiedContentUseCase.RegistrationResult result) {
            List<String> resources = new java.util.ArrayList<>();
            if (!result.creatorCreated()) {
                resources.add("creator");
            }
            if (!result.videoCreated()) {
                resources.add("video");
            }
            return List.copyOf(resources);
        }

        private static JsonNode readTree(ObjectMapper mapper, String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            return mapper.readTree(json);
        }
    }
}
