package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;

/**
 * {@code review}의 {@code DISCARD} 최종 반영(외부 호출 없는 순수 DB 쓰기)만 소유하는 짧은
 * 트랜잭션이다. {@link RegistrationUnitConfirmCommitService}와 같은 이유로 등록 단위 상태 전이와
 * 감사 이력 삽입 두 쓰기를 하나로 묶어, 감사 이력 삽입이 실패해도 이미 반영한 상태 전이가 함께
 * 롤백되게 한다(감사 이력 없이 폐기만 반영되는 상태를 방지한다).
 *
 * <p>{@code expectedReviewStatus}가 더 이상 일치하지 않으면(동시 요청이 먼저 반영) 아무것도 쓰지
 * 않고 {@code false}를 반환한다.</p>
 */
@Service
class RegistrationUnitDiscardCommitService {

    private final AiRegistrationUnitStore registrationUnitStore;
    private final AiRegistrationUnitReviewStore registrationUnitReviewStore;

    RegistrationUnitDiscardCommitService(
            AiRegistrationUnitStore registrationUnitStore,
            AiRegistrationUnitReviewStore registrationUnitReviewStore) {
        this.registrationUnitStore = registrationUnitStore;
        this.registrationUnitReviewStore = registrationUnitReviewStore;
    }

    @Transactional
    boolean commit(UUID unitId, String expectedReviewStatus, String reason, UUID adminId) {
        boolean updated = registrationUnitStore.discard(unitId, expectedReviewStatus, OffsetDateTime.now());
        if (!updated) {
            return false;
        }
        registrationUnitReviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unitId, "DISCARD", reason, null, null, null, adminId));
        return true;
    }
}
