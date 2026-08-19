package com.masiton.ai.application.port.out;

import java.util.UUID;

/**
 * {@code ai_registration_unit_review} append-only 감사 이력 저장 Port다. 데이터 계약 5.3절과
 * {@code V6__add_ai_registration_unit_and_food_category_mapping.sql}의 CHECK 제약을 그대로 따른다.
 * {@code submittedSupplementsJson}은 {@code CONFIRM}, {@code previousCategoryDecisionJson}은
 * {@code ADJUST_CATEGORY}, {@code revertedRegistrationJson}은 {@code ROLLBACK}일 때만 값을 가진다.
 */
public interface AiRegistrationUnitReviewStore {

    UUID insert(RegistrationUnitReviewInsert insert);

    record RegistrationUnitReviewInsert(
            UUID registrationUnitId,
            String decision,
            String reason,
            String submittedSupplementsJson,
            String previousCategoryDecisionJson,
            String revertedRegistrationJson,
            UUID reviewedBy) {
    }
}
