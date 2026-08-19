package com.masiton.ai.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.common.security.LegacyAdminActorResolver;

/**
 * {@code review}의 {@code CONFIRM} 최종 반영(외부 호출이 모두 끝난 뒤의 순수 DB 쓰기)만 소유하는
 * 짧은 트랜잭션이다. 등록 단위 상태 전이, 태그 연결·감사, 검토 감사 이력 세 쓰기를 하나로 묶어
 * 커밋한다. {@link AiExtractionResultCommitService}와 같은 이유로, Kakao 검증 같은 외부 호출은
 * 호출자가 이 서비스를 부르기 전에 끝낸 뒤 이미 계산된 결과만 들어온다.
 *
 * <p>{@code expectedReviewStatus}가 더 이상 일치하지 않으면(동시 요청이 먼저 반영) 아무것도 쓰지
 * 않고 {@code false}를 반환한다. 태그 연결이나 감사 이력 삽입이 실패하면 이미 반영한 등록 단위
 * 상태 전이도 함께 롤백되어, 태그·감사 없이 등록만 남는 상태를 방지한다.</p>
 */
@Service
class RegistrationUnitConfirmCommitService {

    private final AiRegistrationUnitStore registrationUnitStore;
    private final AiRegistrationUnitReviewStore registrationUnitReviewStore;
    private final AiExtractionAdminQueryPort port;
    private final LegacyAdminActorResolver legacyAdminActorResolver;

    RegistrationUnitConfirmCommitService(
            AiRegistrationUnitStore registrationUnitStore,
            AiRegistrationUnitReviewStore registrationUnitReviewStore,
            AiExtractionAdminQueryPort port,
            LegacyAdminActorResolver legacyAdminActorResolver) {
        this.registrationUnitStore = registrationUnitStore;
        this.registrationUnitReviewStore = registrationUnitReviewStore;
        this.port = port;
        this.legacyAdminActorResolver = legacyAdminActorResolver;
    }

    @Transactional
    boolean commit(UUID unitId, String expectedReviewStatus, AiRegistrationUnitStore.RegisteredResult registered,
                   UUID snapshotId, UUID visitId, List<AiExtractionAdminQueryPort.TagDecision> tagDecisions,
                   UUID adminId, String reason, String submittedSupplementsJson) {
        boolean updated = registrationUnitStore.confirmWithSupplement(unitId, expectedReviewStatus, registered);
        if (!updated) {
            return false;
        }
        if (!tagDecisions.isEmpty()) {
            List<AiExtractionAdminQueryPort.TagDecision> attached =
                    port.connectConfirmedTags(snapshotId, visitId, tagDecisions);
            if (!attached.isEmpty()) {
                // ai_candidate_tag_review.reviewed_by is still legacy-FK'd to admin_account(id) (V4),
                // unlike ai_registration_unit_review.reviewed_by below which targets member_account(id)
                // directly (V8). adminId here is the raw member_account id from the JWT subject.
                port.appendTagOverrides(snapshotId, legacyAdminActorResolver.resolve(adminId), reason, attached);
            }
        }
        registrationUnitReviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unitId, "CONFIRM", reason, submittedSupplementsJson, null, null, adminId));
        return true;
    }
}
