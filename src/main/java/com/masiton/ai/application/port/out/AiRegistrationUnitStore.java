package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code ai_registration_unit} 저장 Port다. 데이터 계약 5.1절과
 * {@code V8__add_ai_registration_unit_and_food_category_mapping.sql}의 컬럼·CHECK 제약을 그대로
 * 따른다. 특히 {@code place_decision}·{@code category_decision}은 네 등록 결과 식별자와 함께
 * 모두 존재하거나 모두 {@code NULL}이어야 하므로({@code ck_ai_registration_unit__registration_result_pair}),
 * 등록이 완료되지 않은 상태({@code AUTO_BLOCKED}·{@code AUTO_REJECTED})로 {@link #insert}를 호출할 때는
 * 두 값을 {@code null}로 남긴다.
 */
public interface AiRegistrationUnitStore {

    /** 등록 단위의 초기 판정 상태를 삽입하고 새로 만든 행의 식별자를 반환한다. */
    UUID insert(RegistrationUnitInsert insert);

    /**
     * 이미 존재하는 등록 단위 행을 정식 등록 완료 상태로 갱신한다. {@code review_status}를
     * {@code AUTO_CONFIRMED}로 바꾸고 {@code executedBy}는 이 등록을 실행한 주체(관리자 등록 단위
     * 일괄 등록 API 3.6절에서는 {@code ADMIN})로 갱신한다.
     */
    void markRegistered(UUID unitId, RegisteredResult registered);

    /** Snapshot 하나가 가진 등록 단위 전체를 {@code unit_index} 오름차순으로 조회한다. */
    List<RegistrationUnitRow> findBySnapshotId(UUID snapshotId);

    /**
     * 작업의 가장 최신 Snapshot이 가진 등록 단위 전체를 {@code unit_index} 오름차순으로 조회한다.
     * 잠그지 않으며, {@code review}·등록 단위 일괄 등록 API의 {@code unitId} 0/1/2개 이상 판정에
     * 쓴다. Snapshot이 없거나 등록 단위가 없으면 빈 목록이다.
     */
    List<RegistrationUnitRow> findByJobId(UUID jobId);

    /**
     * 지정한 {@code unitId}가 그 작업의 가장 최신 Snapshot이 가진 등록 단위인지 확인하고 행을
     * 잠근다({@code SELECT ... FOR UPDATE NOWAIT}). 존재하지 않으면 빈 값을 반환하고, 다른
     * 트랜잭션이 이미 잠갔으면 {@link AiRegistrationUnitConcurrentAccessException}을 던진다.
     */
    Optional<RegistrationUnitRow> lockByJobAndUnitId(UUID jobId, UUID unitId);

    /**
     * {@code review}의 {@code CONFIRM} 보충 입력 성공 결과를 반영한다. {@code review_status}를
     * {@code MANUAL_OVERRIDE}로 바꾸되 {@code executed_by}는 바꾸지 않는다.
     */
    void confirmWithSupplement(UUID unitId, RegisteredResult registered);

    /**
     * {@code review}의 {@code ROLLBACK}을 반영한다. {@code review_status}를 {@code MANUAL_OVERRIDE}로,
     * {@code rolled_back_at}을 채우고 등록 결과 4종·{@code place_decision}·{@code category_decision}·
     * {@code reused_resources}를 초기화한다.
     */
    void rollback(UUID unitId, OffsetDateTime rolledBackAt);

    /**
     * {@code review}의 {@code DISCARD}를 반영한다. {@code review_status}를 {@code MANUAL_OVERRIDE}로,
     * {@code discarded_at}을 채우고 {@code block_reason}을 지운다. 등록 결과 컬럼은 이미 모두
     * {@code NULL}이므로 건드리지 않는다.
     */
    void discard(UUID unitId, OffsetDateTime discardedAt);

    /**
     * {@code review}의 {@code ADJUST_CATEGORY}를 반영한다. {@code review_status}를
     * {@code MANUAL_OVERRIDE}로 바꾸고 {@code category_decision}만 교체한다. 등록 결과·
     * {@code place_decision}·{@code executed_by}는 그대로 둔다.
     */
    void adjustCategory(UUID unitId, String categoryDecisionJson);

    /**
     * {@code placeDecisionJson}·{@code categoryDecisionJson}과 등록 결과 식별자 4종은 모두
     * {@code null}이거나 모두 값을 가져야 한다. DB CHECK 제약이 이를 강제한다.
     */
    record RegistrationUnitInsert(
            UUID snapshotId,
            int unitIndex,
            String restaurantName,
            String reviewStatus,
            String blockReason,
            String placeDecisionJson,
            String categoryDecisionJson,
            UUID registeredRestaurantId,
            UUID registeredCreatorId,
            UUID registeredVideoId,
            UUID registeredVisitId,
            String reusedResourcesJson,
            String executedBy,
            OffsetDateTime decidedAt) {
    }

    record RegisteredResult(
            UUID registeredRestaurantId,
            UUID registeredCreatorId,
            UUID registeredVideoId,
            UUID registeredVisitId,
            String reusedResourcesJson,
            String placeDecisionJson,
            String categoryDecisionJson,
            String executedBy) {
    }

    record RegistrationUnitRow(
            UUID id,
            UUID snapshotId,
            int unitIndex,
            String restaurantName,
            String reviewStatus,
            String blockReason,
            String placeDecisionJson,
            String categoryDecisionJson,
            UUID registeredRestaurantId,
            UUID registeredCreatorId,
            UUID registeredVideoId,
            UUID registeredVisitId,
            List<String> reusedResources,
            String executedBy,
            OffsetDateTime decidedAt,
            OffsetDateTime rolledBackAt,
            OffsetDateTime discardedAt) {

        public String manualOverrideType() {
            if (rolledBackAt != null) {
                return "ROLLED_BACK";
            }
            if (discardedAt != null) {
                return "DISCARDED";
            }
            return null;
        }

        /** 등록 유지 상태(사후 보정 등록·카테고리 보정)는 {@code MANUAL_OVERRIDE}이고 등록 결과가 모두 존재하는 조합이다. */
        public boolean isRegistered() {
            return registeredRestaurantId != null
                    && ("AUTO_CONFIRMED".equals(reviewStatus)
                        || ("MANUAL_OVERRIDE".equals(reviewStatus) && rolledBackAt == null && discardedAt == null));
        }
    }
}
