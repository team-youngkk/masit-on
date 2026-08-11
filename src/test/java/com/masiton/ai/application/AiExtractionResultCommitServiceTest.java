package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 추출 결과 커밋 서비스")
class AiExtractionResultCommitServiceTest {

    private final AiExtractionResultStore resultStore = mock(AiExtractionResultStore.class);
    private final AutoRegisterVerifiedContentUseCase autoRegister = mock(AutoRegisterVerifiedContentUseCase.class);
    private final AiExtractionResultCommitService service = new AiExtractionResultCommitService(
            resultStore, autoRegister, new ObjectMapper());
    private final UUID jobId = UUID.randomUUID();
    private final OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");

    @Test
    @DisplayName("검증 실패는 후보와 시도만 저장하고 정식 등록을 호출하지 않는다")
    void persistBlocked_검증실패_정식등록없이후보와시도를저장한다() {
        // Given
        AiExtractionResultCommitService.ProcessCommand command = command(List.of());
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1))
                .willReturn(Optional.of(job()));
        given(resultStore.nextSnapshotVersion(jobId)).willReturn(1);
        given(resultStore.insertSnapshot(any(), anyInt(), anyString(), anyString(), anyString(), anyString(),
                anyString(), eq("AUTO_BLOCKED"), anyString(), any(), any())).willReturn(UUID.randomUUID());

        // When
        boolean committed = service.persistBlocked(command);

        // Then
        assertThat(committed).isTrue();
        verify(resultStore).completeSuccess(eq(jobId), eq("worker-1"), eq(1), eq("COMPLETE"), any(), eq(finishedAt),
                eq("request-1"));
        verify(autoRegister, never()).register(any());
    }

    @Test
    @DisplayName("검증 성공은 정식 등록과 VisitTag를 같은 결과 커밋으로 연결한다")
    void persistConfirmed_검증성공_정식등록과VisitTag를연결한다() {
        // Given
        UUID tagId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        AiExtractionResultCommitService.AiTagCandidate tag = new AiExtractionResultCommitService.AiTagCandidate(
                "tag-1", "MENU", "MENU_NAENGMYEON", "냉면", BigDecimal.valueOf(0.9),
                "{\"type\":\"TIMESTAMP\",\"startMs\":1,\"endMs\":2}",
                "[\"냉면\"]", "gemini-3-flash-preview/P1/S1", "AUTO_MERGE", null, true, tagId);
        AiExtractionResultCommitService.ProcessCommand command = command(List.of(tag));
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1))
                .willReturn(Optional.of(job()));
        given(resultStore.nextSnapshotVersion(jobId)).willReturn(1);
        given(resultStore.insertSnapshot(any(), anyInt(), anyString(), anyString(), anyString(), anyString(),
                anyString(), eq("AUTO_CONFIRMED"), eq(null), any(), any())).willReturn(UUID.randomUUID());
        given(resultStore.findTagForUpdate("MENU_NAENGMYEON"))
                .willReturn(Optional.of(new AiExtractionResultStore.TagDefinition(
                        tagId, "MENU_NAENGMYEON", "MENU", "냉면", "[]", "ACTIVE")));
        given(autoRegister.register(any())).willReturn(new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), visitId, true, true, true, true));

        // When
        boolean committed = service.persistConfirmed(command, mock(AutoRegisterVerifiedContentUseCase.VerifiedContentCommand.class));

        // Then
        assertThat(committed).isTrue();
        verify(autoRegister).register(any());
        verify(resultStore).insertVisitTag(eq(visitId), eq(tagId), eq(BigDecimal.valueOf(0.9)), anyString(),
                eq("gemini-3-flash-preview/P1/S1"), eq(finishedAt));
        verify(resultStore).completeSuccess(eq(jobId), eq("worker-1"), eq(1), eq("COMPLETE"), any(), eq(finishedAt),
                eq("request-1"));
    }

    @Test
    @DisplayName("정식 등록 중 실패하면 AI 작업 완료와 후속 태그 저장을 호출하지 않는다")
    void persistConfirmed_정식등록실패_작업완료를기록하지않는다() {
        // Given
        AiExtractionResultCommitService.ProcessCommand command = command(List.of());
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1)).willReturn(Optional.of(job()));
        given(resultStore.nextSnapshotVersion(jobId)).willReturn(1);
        given(resultStore.insertSnapshot(any(), anyInt(), anyString(), anyString(), anyString(), anyString(),
                anyString(), eq("AUTO_CONFIRMED"), eq(null), any(), any())).willReturn(UUID.randomUUID());
        given(autoRegister.register(any())).willThrow(new IllegalStateException("registration failure"));

        // When / Then
        assertThatThrownBy(() -> service.persistConfirmed(command,
                mock(AutoRegisterVerifiedContentUseCase.VerifiedContentCommand.class)))
                .isInstanceOf(IllegalStateException.class);
        verify(resultStore, never()).completeSuccess(any(), anyString(), anyInt(), anyString(), any(), any(), any());
        verify(resultStore, never()).insertVisitTag(any(), any(), any(), anyString(), anyString(), any());
    }

    private AiExtractionResultStore.ProcessingJob job() {
        return new AiExtractionResultStore.ProcessingJob(jobId, "channel-1", "video-1",
                URI.create("https://www.youtube.com/watch?v=video-1"));
    }

    private AiExtractionResultCommitService.ProcessCommand command(
            List<AiExtractionResultCommitService.AiTagCandidate> tags) {
        return new AiExtractionResultCommitService.ProcessCommand(
                jobId, "worker-1", 1, finishedAt.minusSeconds(5), finishedAt, "request-1", "COMPLETE",
                "{}", "[]", "{}", "{}", "[]", "VALIDATION_FAILED", "AUTO_BLOCKED", tags);
    }
}
