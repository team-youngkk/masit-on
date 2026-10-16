package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.orchestration.application.port.in.AdjustRegisteredCategoryUseCase;
import com.masiton.orchestration.application.port.in.RollbackAiRegisteredContentUseCase;

/** 외부 호출 없는 사후 변경의 단위 CAS·공개 자원 변경·감사를 함께 커밋한다. */
@Service
public class RegistrationUnitPostRegistrationCommitService {

    private final AiRegistrationUnitStore unitStore;
    private final AiRegistrationUnitReviewStore reviewStore;
    private final RollbackAiRegisteredContentUseCase rollbackUseCase;
    private final AdjustRegisteredCategoryUseCase adjustCategoryUseCase;

    public RegistrationUnitPostRegistrationCommitService(AiRegistrationUnitStore unitStore,
            AiRegistrationUnitReviewStore reviewStore, RollbackAiRegisteredContentUseCase rollbackUseCase,
            AdjustRegisteredCategoryUseCase adjustCategoryUseCase) {
        this.unitStore = unitStore;
        this.reviewStore = reviewStore;
        this.rollbackUseCase = rollbackUseCase;
        this.adjustCategoryUseCase = adjustCategoryUseCase;
    }

    @Transactional
    public boolean rollback(AiRegistrationUnitStore.RegistrationUnitRow unit, String reason,
            String revertedResourcesJson, UUID adminId) {
        if (!unitStore.rollback(unit.id(), unit.reviewStatus(), OffsetDateTime.now())) {
            return false;
        }
        rollbackUseCase.rollback(new RollbackAiRegisteredContentUseCase.RegistrationReference(
                unit.snapshotId(), unit.registeredRestaurantId(), !unit.reusedResources().contains("restaurant"),
                unit.registeredCreatorId(), !unit.reusedResources().contains("creator"), unit.registeredVideoId(),
                !unit.reusedResources().contains("video"), unit.registeredVisitId(),
                !unit.reusedResources().contains("visit"), unit.id()));
        reviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unit.id(), "ROLLBACK", reason, null, null, revertedResourcesJson, adminId));
        return true;
    }

    @Transactional
    public boolean adjustCategory(AiRegistrationUnitStore.RegistrationUnitRow unit, UUID categoryId,
            String categoryDecisionJson, String reason, UUID adminId) {
        if (!unitStore.adjustCategory(unit.id(), unit.reviewStatus(), categoryDecisionJson)) {
            return false;
        }
        adjustCategoryUseCase.adjust(unit.registeredRestaurantId(), categoryId);
        reviewStore.insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unit.id(), "ADJUST_CATEGORY", reason, null, unit.categoryDecisionJson(), null, adminId));
        return true;
    }
}
