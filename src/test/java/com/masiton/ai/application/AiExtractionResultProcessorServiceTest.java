package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 추출 결과 처리기")
class AiExtractionResultProcessorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiExtractionResultStore resultStore = mock(AiExtractionResultStore.class);
    private final AiExtractionResultCommitService commitService = mock(AiExtractionResultCommitService.class);
    private final VerifyAiContentCandidateUseCase contentVerification = mock(VerifyAiContentCandidateUseCase.class);
    private final AiExtractionResultProcessorService processor = new AiExtractionResultProcessorService(
            resultStore, commitService, contentVerification, objectMapper);

    @Test
    @DisplayName("필수 후보와 외부 검증이 통과하면 등록 커밋으로 전달한다")
    void process_필수후보와외부검증통과_원자등록커밋으로전달한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        UUID regionId = UUID.randomUUID();
        UUID foodCategoryId = UUID.randomUUID();
        given(contentVerification.verify(any())).willReturn(VerifyAiContentCandidateUseCase.VerificationResult.verified(
                new VerifyAiContentCandidateUseCase.VerifiedContent(
                regionId, foodCategoryId, "맛집", "kakao-1", "https://place.map.kakao.com/123",
                "서울특별시 마포구 월드컵로 1", "02-1234-5678", java.math.BigDecimal.valueOf(126.9),
                java.math.BigDecimal.valueOf(37.5), "channel-1", "채널", "https://www.youtube.com/channel/channel-1",
                "video-1", "영상 제목", "https://www.youtube.com/watch?v=video-1",
                "https://img.youtube.com/vi/video-1/0.jpg", finishedAt, finishedAt)));
        given(resultStore.findTag("MENU_NAENGMYEON")).willReturn(Optional.of(
                new AiExtractionResultStore.TagDefinition(UUID.randomUUID(), "MENU_NAENGMYEON", "MENU", "냉면",
                        "[]", "ACTIVE")));
        given(commitService.persistConfirmed(any(), any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"visit-hash"}},
                          {"field":"tag","candidateTagId":"tag-1","tagType":"MENU","rawLabel":"냉면","normalizedCode":"MENU_NAENGMYEON","label":"냉면","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":2,"endMs":3}}
                        ],"missingFields":[]}
                        """));

        // Then
        assertThat(processed).isTrue();
        verify(commitService).persistConfirmed(any(), any());
        var verificationCommand = forClass(VerifyAiContentCandidateUseCase.VerificationCommand.class);
        verify(contentVerification).verify(verificationCommand.capture());
        assertThat(verificationCommand.getValue().visitEvidence().value()).isEqualTo("직접 방문");
        assertThat(verificationCommand.getValue().visitEvidence().evidence().type())
                .isEqualTo(VerifyAiContentCandidateUseCase.EvidenceType.TEXT_RANGE);
    }

    @Test
    @DisplayName("외부 검증이 실제 방문 근거를 확정하지 않으면 정식 등록을 호출하지 않는다")
    void process_외부방문근거미확정_정식등록하지않는다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(contentVerification.verify(any())).willReturn(
                VerifyAiContentCandidateUseCase.VerificationResult.blocked("VISIT_EVIDENCE_REQUIRED"));
        given(commitService.persistBlocked(any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, "worker-1", 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":[]}
                        """));

        // Then
        assertThat(processed).isTrue();
        var blockedCommand = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(blockedCommand.capture());
        assertThat(blockedCommand.getValue().blockReason()).isEqualTo("VISIT_EVIDENCE_REQUIRED");
        verify(commitService, never()).persistConfirmed(any(), any());
    }

    @Test
    @DisplayName("필수 후보가 누락되면 외부 검증과 정식 등록을 호출하지 않는다")
    void process_필수후보누락_자동보류하고외부검증하지않는다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(commitService.persistBlocked(any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"PARTIAL","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":["address","location","visitEvidence"]}
                        """));

        // Then
        assertThat(processed).isTrue();
        verify(commitService).persistBlocked(any());
        verifyNoInteractions(contentVerification);
    }

    @Test
    @DisplayName("허용되지 않은 완결성 값은 PARTIAL로 정규화해 작업 CHECK 위반을 막는다")
    void process_허용되지않은완결성값_부분완료로정규화한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(commitService.persistBlocked(any())).willReturn(true);

        // When
        processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("{\"resultCompleteness\":\"DONE\",\"candidates\":[],\"missingFields\":[]}"));

        // Then
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(command.capture());
        assertThat(command.getValue().resultCompleteness()).isEqualTo("PARTIAL");
        assertThat(command.getValue().blockReason()).isEqualTo("INVALID_RESULT_COMPLETENESS");
    }

    private AiVideoExtractionResult result(String json) throws Exception {
        JsonNode payload = objectMapper.readTree(json);
        return new AiVideoExtractionResult(payload, "provider-request-1");
    }
}
