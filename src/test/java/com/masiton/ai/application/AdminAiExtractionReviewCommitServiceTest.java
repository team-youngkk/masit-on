package com.masiton.ai.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.RollbackAiRegisteredContentUseCase;
import com.masiton.common.web.BusinessException;

@DisplayName("관리자 AI 추출 검토 커밋 서비스")
class AdminAiExtractionReviewCommitServiceTest {
    private final AiExtractionAdminQueryPort port = mock(AiExtractionAdminQueryPort.class);
    private final AutoRegisterVerifiedContentUseCase autoRegister = mock(AutoRegisterVerifiedContentUseCase.class);
    private final RollbackAiRegisteredContentUseCase rollback = mock(RollbackAiRegisteredContentUseCase.class);
    private final AdminAiExtractionReviewCommitService service =
            new AdminAiExtractionReviewCommitService(port, autoRegister, rollback);

    @Test
    @DisplayName("수동 확정으로 연결한 모든 태그를 MANUAL_OVERRIDE 감사 이력으로 남긴다")
    void confirm_연결된모든태그_수동보정감사이력으로남긴다() {
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID overrideSnapshotId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        List<AiExtractionAdminQueryPort.TagDecision> attached = List.of(
                new AiExtractionAdminQueryPort.TagDecision("tag-1", "MANUAL_OVERRIDE", "MENU_NAENGMYEON"));
        AiExtractionAdminQueryPort.ReviewTarget target = new AiExtractionAdminQueryPort.ReviewTarget(
                snapshotId, "AUTO_BLOCKED", jobId, "channel", "video", "https://www.youtube.com/watch?v=video",
                null, null, null, null, null);
        when(port.reviewTarget(jobId)).thenReturn(Optional.of(target));
        when(autoRegister.register(any())).thenReturn(new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                null, null, null, UUID.randomUUID(), false, false, false, true));
        when(port.connectConfirmedTags(eq(snapshotId), any(UUID.class), anyList())).thenReturn(attached);
        when(port.override(snapshotId, "AUTO_BLOCKED", adminId, "확인", "CONFIRM"))
                .thenReturn(overrideSnapshotId);

        service.confirm(jobId, "AUTO_BLOCKED", adminId, "확인", List.of(), null);

        verify(port).appendTagOverrides(overrideSnapshotId, adminId, "확인", attached);
    }

    @Test
    @DisplayName("재사용된 Visit은 공개 상태와 태그 소유권이 모호하므로 롤백하지 않는다")
    void rollback_재사용Visit_409로거부하고롤백을호출하지않는다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionAdminQueryPort.ReviewTarget target = new AiExtractionAdminQueryPort.ReviewTarget(
                UUID.randomUUID(), "AUTO_CONFIRMED", jobId, "channel", "video", "https://www.youtube.com/watch?v=video",
                null, null, null, null, new AiExtractionAdminQueryPort.RegisteredContent(
                        null, false, null, false, null, false, UUID.randomUUID(), false));
        when(port.reviewTarget(jobId)).thenReturn(Optional.of(target));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.rollback(jobId, "AUTO_CONFIRMED", UUID.randomUUID(), "오등록", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reused");
        verify(rollback, never()).rollback(any());
    }

    @Test
    @DisplayName("수동 확정 Snapshot에서 롤백할 때 원본 등록 Snapshot provenance를 전달한다")
    void rollback_수동확정Snapshot_원본등록Snapshot을롤백에전달한다() {
        UUID jobId = UUID.randomUUID();
        UUID originalSnapshotId = UUID.randomUUID();
        UUID overrideSnapshotId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AiExtractionAdminQueryPort.ReviewTarget target = new AiExtractionAdminQueryPort.ReviewTarget(
                overrideSnapshotId, "AUTO_CONFIRMED", jobId, "channel", "video", "https://www.youtube.com/watch?v=video",
                null, null, null, null, new AiExtractionAdminQueryPort.RegisteredContent(
                        null, false, null, false, null, false, visitId, true, originalSnapshotId));
        when(port.reviewTarget(jobId)).thenReturn(Optional.of(target));
        when(port.override(overrideSnapshotId, "AUTO_CONFIRMED", adminId, "오등록", "ROLLBACK"))
                .thenReturn(UUID.randomUUID());

        service.rollback(jobId, "AUTO_CONFIRMED", adminId, "오등록", List.of());

        verify(rollback).rollback(new RollbackAiRegisteredContentUseCase.RegistrationReference(
                originalSnapshotId, null, false, null, false, null, false, visitId, true));
    }
}
