package com.masiton.ai.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.RollbackAiRegisteredContentUseCase;
import com.masiton.common.web.BusinessException;

/** 검증 완료 결과와 Snapshot/audit 상태 변경을 하나의 짧은 DB 트랜잭션으로 커밋한다. */
@Service
public class AdminAiExtractionReviewCommitService {
    private final AiExtractionAdminQueryPort port;
    private final AutoRegisterVerifiedContentUseCase autoRegister;
    private final RollbackAiRegisteredContentUseCase rollback;

    public AdminAiExtractionReviewCommitService(AiExtractionAdminQueryPort port,
                                                AutoRegisterVerifiedContentUseCase autoRegister,
                                                RollbackAiRegisteredContentUseCase rollback) {
        this.port = port;
        this.autoRegister = autoRegister;
        this.rollback = rollback;
    }

    @Transactional
    public void confirm(UUID jobId, String expected, UUID adminId, String reason,
                        List<AiExtractionAdminQueryPort.TagDecision> tags,
                        AutoRegisterVerifiedContentUseCase.VerifiedContentCommand command) {
        AiExtractionAdminQueryPort.ReviewTarget target = current(jobId, expected);
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = autoRegister.register(command);
        port.markRegisteredContent(target.snapshotId(), new AiExtractionAdminQueryPort.RegisteredContent(
                registration.restaurantId(), registration.restaurantCreated(), registration.creatorId(), registration.creatorCreated(),
                registration.videoId(), registration.videoCreated(), registration.visitId(), registration.visitCreated()));
        List<AiExtractionAdminQueryPort.TagDecision> attachedTags =
                port.connectConfirmedTags(target.snapshotId(), registration.visitId(), tags);
        complete(target, expected, adminId, reason, "CONFIRM", attachedTags);
    }

    @Transactional
    public void rollback(UUID jobId, String expected, UUID adminId, String reason,
                         List<AiExtractionAdminQueryPort.TagDecision> tags) {
        AiExtractionAdminQueryPort.ReviewTarget target = current(jobId, expected);
        AiExtractionAdminQueryPort.RegisteredContent registered = target.registeredContent();
        if (registered == null || registered.registrationSnapshotId() == null || registered.visitId() == null
                || !registered.visitCreated()) {
            throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_DUPLICATE_CONFLICT", "The registered Visit was reused and cannot be rolled back safely.");
        }
        rollback.rollback(new RollbackAiRegisteredContentUseCase.RegistrationReference(
                registered.registrationSnapshotId(), registered.restaurantId(), registered.restaurantCreated(), registered.creatorId(), registered.creatorCreated(),
                registered.videoId(), registered.videoCreated(), registered.visitId(), registered.visitCreated()));
        complete(target, expected, adminId, reason, "ROLLBACK", tags);
    }

    @Transactional
    public void discard(UUID jobId, String expected, UUID adminId, String reason,
                        List<AiExtractionAdminQueryPort.TagDecision> tags) {
        AiExtractionAdminQueryPort.ReviewTarget target = current(jobId, expected);
        complete(target, expected, adminId, reason, "DISCARD", tags);
    }

    private AiExtractionAdminQueryPort.ReviewTarget current(UUID jobId, String expected) {
        AiExtractionAdminQueryPort.ReviewTarget target = port.reviewTarget(jobId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "AIEXTRACT_JOB_NOT_FOUND", "The AI extraction job was not found."));
        if (!expected.equals(target.reviewStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_DUPLICATE_CONFLICT", "Review status is stale.");
        }
        return target;
    }

    private void complete(AiExtractionAdminQueryPort.ReviewTarget target, String expected, UUID adminId,
                          String reason, String decision, List<AiExtractionAdminQueryPort.TagDecision> tags) {
        UUID overrideSnapshotId = port.override(target.snapshotId(), expected, adminId, reason, decision);
        if (overrideSnapshotId == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_DUPLICATE_CONFLICT", "Review status is stale.");
        }
        port.appendTagOverrides(overrideSnapshotId, adminId, reason, tags);
    }
}
