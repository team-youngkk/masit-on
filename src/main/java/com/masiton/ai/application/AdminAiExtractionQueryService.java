package com.masiton.ai.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.common.web.BusinessException;

@Service
public class AdminAiExtractionQueryService {
    private final AiExtractionAdminQueryPort port;
    private final AiRegistrationUnitStore registrationUnitStore;
    private final RegistrationUnitCommandService registrationUnitCommandService;

    public AdminAiExtractionQueryService(AiExtractionAdminQueryPort port,
                                         AiRegistrationUnitStore registrationUnitStore,
                                         RegistrationUnitCommandService registrationUnitCommandService) {
        this.port = port;
        this.registrationUnitStore = registrationUnitStore;
        this.registrationUnitCommandService = registrationUnitCommandService;
    }

    @Transactional(readOnly = true)
    public AiExtractionAdminQueryPort.Page list(String executionStatus, String source, String reviewStatus,
                                                int page, int size) {
        return port.list(executionStatus, source, reviewStatus, (page - 1) * size, size);
    }

    @Transactional(readOnly = true)
    public AdminJobDetail detail(UUID jobId) {
        AiExtractionAdminQueryPort.Detail detail = port.detail(jobId).orElseThrow(this::jobNotFound);
        List<AiRegistrationUnitStore.RegistrationUnitRow> registrationUnits =
                registrationUnitStore.findByJobId(jobId);
        List<String> unitStatuses = registrationUnits.stream()
                .map(AiRegistrationUnitStore.RegistrationUnitRow::reviewStatus)
                .toList();
        String topReviewStatus = JobReviewStatusSummary.summarize(detail.job().reviewStatus(), unitStatuses);
        return new AdminJobDetail(detail, registrationUnits, topReviewStatus);
    }

    @Transactional(readOnly = true)
    public String retryUrl(UUID jobId) {
        AiExtractionAdminQueryPort.RetryTarget target = port.retryTarget(jobId)
                .orElseThrow(this::jobNotFound);
        boolean retryable = "FAILED".equals(target.executionStatus())
                || ("SUCCEEDED".equals(target.executionStatus())
                    && ("PARTIAL".equals(target.resultCompleteness()) || hasAutoBlockedRegistrationUnit(jobId)));
        if (!retryable) {
            throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_RETRY_BLOCKED", "The job is not retryable.");
        }
        return target.videoUrl();
    }

    private boolean hasAutoBlockedRegistrationUnit(UUID jobId) {
        return registrationUnitStore.findByJobId(jobId).stream()
                .anyMatch(unit -> "AUTO_BLOCKED".equals(unit.reviewStatus()));
    }

    /** API 3.5절: 등록 단위 granularity 사후 보정·롤백. {@link RegistrationUnitCommandService}로 위임한다. */
    public void review(UUID jobId, String decision, String unitId, String reason, String suppliedKakaoPlaceUrl,
                       String suppliedFoodCategoryId, List<AiExtractionAdminQueryPort.TagDecision> tagDecisions,
                       UUID adminId) {
        registrationUnitCommandService.review(jobId, decision, unitId, reason, suppliedKakaoPlaceUrl,
                suppliedFoodCategoryId, tagDecisions, adminId);
    }

    /** API 3.6절: 등록 단위 일괄 등록. {@link RegistrationUnitCommandService}로 위임한다. */
    public RegistrationUnitCommandService.RegistrationExecutionView registerUnit(UUID jobId, UUID unitId) {
        return registrationUnitCommandService.registerUnit(jobId, unitId);
    }

    /** API 3.10절: AUTO_BLOCKED 등록 단위 일괄 폐기. {@link RegistrationUnitCommandService}로 위임한다. */
    public List<UUID> discardAllBlocked(UUID jobId, String reason, UUID adminId) {
        return registrationUnitCommandService.discardAllBlocked(jobId, reason, adminId);
    }

    private BusinessException jobNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "AIEXTRACT_JOB_NOT_FOUND",
                "The AI extraction job was not found.");
    }

    /** 작업 상세 응답이 필요로 하는 조회 결과를 하나로 묶는다. */
    public record AdminJobDetail(
            AiExtractionAdminQueryPort.Detail detail,
            List<AiRegistrationUnitStore.RegistrationUnitRow> registrationUnits,
            String topReviewStatus) {
    }
}
