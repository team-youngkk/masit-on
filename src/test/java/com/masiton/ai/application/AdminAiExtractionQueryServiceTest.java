package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
@DisplayName("관리자 AI 추출 검토 서비스")
class AdminAiExtractionQueryServiceTest {
    private final AiExtractionAdminQueryPort port = mock(AiExtractionAdminQueryPort.class);
    private final com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase verifier = mock(com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.class);
    private final AdminAiExtractionReviewCommitService reviewCommit = mock(AdminAiExtractionReviewCommitService.class);
    private final AdminAiExtractionQueryService service = new AdminAiExtractionQueryService(port, verifier, reviewCommit);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("롤백 요청은 등록 콘텐츠 커밋 경계로 위임한다")
    void review_자동확정상태_롤백커밋경계로위임한다() {
        UUID jobId = UUID.randomUUID();
        when(port.reviewSnapshot(jobId)).thenReturn(java.util.Optional.of(new AiExtractionAdminQueryPort.ReviewTarget(
                UUID.randomUUID(), "AUTO_CONFIRMED", jobId, "channel", "video", "https://www.youtube.com/watch?v=video",
                null, null, null, null, new AiExtractionAdminQueryPort.RegisteredContent(null, false, null, false, null, false, null, false))));
        UUID adminId = UUID.randomUUID();
        service.review(jobId, "ROLLBACK", "AUTO_CONFIRMED", adminId, "오등록", List.of());

        verify(reviewCommit).rollback(jobId, "AUTO_CONFIRMED", adminId, "오등록", List.of());
    }

    @Test
    @DisplayName("복수 후보가 남은 필드가 있으면 확정 검수를 422로 거절하고 정식 등록을 호출하지 않는다")
    void review_복수후보남은필드_확정검수를422로거절한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        JsonNode candidateFields = objectMapper.readTree("""
                {
                  "restaurantName": [
                    {"value":"첫 맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"value":"둘째 맛집","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}}
                  ],
                  "address": "서울시",
                  "location": "https://place.map.kakao.com/123",
                  "visitEvidence": "직접 방문"
                }
                """);
        when(port.reviewSnapshot(jobId)).thenReturn(java.util.Optional.of(new AiExtractionAdminQueryPort.ReviewTarget(
                UUID.randomUUID(), "AUTO_BLOCKED", jobId, "channel", "video", "https://www.youtube.com/watch?v=video",
                candidateFields, objectMapper.createArrayNode(), objectMapper.createObjectNode(), objectMapper.createObjectNode(),
                new AiExtractionAdminQueryPort.RegisteredContent(null, false, null, false, null, false, null, false))));
        UUID adminId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> service.review(jobId, "CONFIRM", "AUTO_BLOCKED", adminId, "복수 후보 중 확인", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AIEXTRACT_VALIDATION_CONFLICT")
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
        verify(reviewCommit, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("방문 근거만 복수 후보이면 확정 검수를 422로 거절하고 정식 등록을 호출하지 않는다")
    void review_방문근거만복수후보_확정검수를422로거절한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        JsonNode candidateFields = objectMapper.readTree("""
                {
                  "restaurantName": "맛집",
                  "address": "서울시",
                  "location": "https://place.map.kakao.com/123",
                  "menu": "냉면",
                  "visitEvidence": [
                    {"value":"직접 방문","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"value":"다시 방문","confidence":0.85,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}}
                  ]
                }
                """);
        when(port.reviewSnapshot(jobId)).thenReturn(java.util.Optional.of(new AiExtractionAdminQueryPort.ReviewTarget(
                UUID.randomUUID(), "AUTO_BLOCKED", jobId, "channel", "video", "https://www.youtube.com/watch?v=video",
                candidateFields, objectMapper.createArrayNode(), objectMapper.createObjectNode(), objectMapper.createObjectNode(),
                new AiExtractionAdminQueryPort.RegisteredContent(null, false, null, false, null, false, null, false))));
        given(verifier.verify(any())).willReturn(
                VerifyAiContentCandidateUseCase.VerificationResult.blocked("VISIT_EVIDENCE_REQUIRED"));
        UUID adminId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> service.review(jobId, "CONFIRM", "AUTO_BLOCKED", adminId, "방문 근거 확인 필요", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AIEXTRACT_VALIDATION_CONFLICT")
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
        verify(reviewCommit, never()).confirm(any(), any(), any(), any(), any(), any());
    }
}
