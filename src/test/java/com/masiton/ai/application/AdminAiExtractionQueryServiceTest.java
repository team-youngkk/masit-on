package com.masiton.ai.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
@DisplayName("관리자 AI 추출 검토 서비스")
class AdminAiExtractionQueryServiceTest {
    private final AiExtractionAdminQueryPort port = mock(AiExtractionAdminQueryPort.class);
    private final com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase verifier = mock(com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.class);
    private final AdminAiExtractionReviewCommitService reviewCommit = mock(AdminAiExtractionReviewCommitService.class);
    private final AdminAiExtractionQueryService service = new AdminAiExtractionQueryService(port, verifier, reviewCommit);

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
}
