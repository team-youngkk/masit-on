package com.masiton.ai.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.AdminAiExtractionQueryService;
import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.GlobalExceptionHandler;

@DisplayName("관리자 AI 영상 추출 Controller API")
class AdminAiVideoExtractionControllerApiTest {

    private final AiExtractionJobUseCase useCase = mock(AiExtractionJobUseCase.class);
    private final AdminAiExtractionQueryService queryService = mock(AdminAiExtractionQueryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AdminAiVideoExtractionController(useCase, queryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("신규 접수는 202와 공통 필드 null 키를 포함한다")
    void submit_신규접수_202와공통필드null키를포함한다() throws Exception {
        when(useCase.submitAdmin(any(), any(), any())).thenReturn(new AiExtractionJobView(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "ADMIN",
                "channel-id",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "QUEUED",
                null,
                null,
                "GOOGLE_GEMINI",
                "gemini-3-flash-preview",
                "P1",
                "S1",
                0,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"),
                null,
                null,
                false
        ));

        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "videoUrl": "https://youtu.be/video-id"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionStatus").value("QUEUED"))
                .andExpect(jsonPath("$.resultCompleteness").hasJsonPath())
                .andExpect(jsonPath("$.resultCompleteness").value(nullValue()))
                .andExpect(jsonPath("$.reviewStatus").hasJsonPath())
                .andExpect(jsonPath("$.reviewStatus").value(nullValue()))
                .andExpect(jsonPath("$.startedAt").hasJsonPath())
                .andExpect(jsonPath("$.startedAt").value(nullValue()))
                .andExpect(jsonPath("$.finishedAt").hasJsonPath())
                .andExpect(jsonPath("$.finishedAt").value(nullValue()))
                .andExpect(jsonPath("$.youtube.channelId").value("channel-id"))
                .andExpect(jsonPath("$.youtube.videoId").value("video-id"))
                .andExpect(jsonPath("$.reused").value(false));
    }

    @Test
    @DisplayName("기존 작업 재사용은 200과 공통 필드 값을 포함한다")
    void submit_기존작업재사용_200과공통필드값을포함한다() throws Exception {
        when(useCase.submitAdmin(any(), any(), any())).thenReturn(new AiExtractionJobView(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "WEBHOOK",
                "channel-id",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "SUCCEEDED",
                "PARTIAL",
                "AUTO_BLOCKED",
                "GOOGLE_GEMINI",
                "gemini-3-flash-preview",
                "P1",
                "S1",
                2,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"),
                OffsetDateTime.parse("2026-08-11T00:01:00Z"),
                OffsetDateTime.parse("2026-08-11T00:03:00Z"),
                true
        ));

        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "videoUrl": "https://www.youtube.com/watch?v=video-id",
                                  "supplementText": "확인 메모"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultCompleteness").value("PARTIAL"))
                .andExpect(jsonPath("$.reviewStatus").value("AUTO_BLOCKED"))
                .andExpect(jsonPath("$.startedAt").value("2026-08-11T00:01:00Z"))
                .andExpect(jsonPath("$.finishedAt").value("2026-08-11T00:03:00Z"))
                .andExpect(jsonPath("$.reused").value(true));
    }

    @Test
    @DisplayName("영상 URL 검증 실패는 400 AIEXTRACT_INVALID_VIDEO_URL을 반환한다")
    void submit_영상URL검증실패_400과계약코드를반환한다() throws Exception {
        when(useCase.submitAdmin(any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "AIEXTRACT_INVALID_VIDEO_URL", "YouTube videoUrl is invalid."));

        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/watch?v=video-id\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AIEXTRACT_INVALID_VIDEO_URL"));
    }

    @Test
    @DisplayName("공백 videoUrl도 400 AIEXTRACT_INVALID_VIDEO_URL로 반환한다")
    void submit_공백videoUrl_400과계약코드를반환한다() throws Exception {
        when(useCase.submitAdmin(any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "AIEXTRACT_INVALID_VIDEO_URL", "YouTube videoUrl is invalid."));

        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AIEXTRACT_INVALID_VIDEO_URL"));

        verify(useCase).submitAdmin("   ", null, null);
    }

    @Test
    @DisplayName("videoUrl 필드가 누락되면 400 MISSING_REQUIRED_FIELD로 반환한다")
    void submit_videoUrl누락_400필수값오류를반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("videoUrl"));
    }

    @Test
    @DisplayName("목록은 1-base 메타데이터와 안정 정렬 결과를 반환한다")
    void list_필터페이지_메타데이터를반환한다() throws Exception {
        when(queryService.list("FAILED", "ADMIN", null, 2, 20)).thenReturn(new AiExtractionAdminQueryPort.Page(
                java.util.List.of(new AiExtractionJobView(UUID.fromString("33333333-3333-4333-8333-333333333333"), "ADMIN", "c", "v", "https://www.youtube.com/watch?v=v", "FAILED", null, null, "GOOGLE_GEMINI", "gemini-3-flash-preview", "P1", "S1", 1, OffsetDateTime.parse("2026-08-11T00:00:00Z"), null, OffsetDateTime.parse("2026-08-11T00:01:00Z"), false)), 41));
        mockMvc.perform(get("/api/admin/ai/video-extractions?executionStatus=FAILED&source=ADMIN&page=2&size=20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].jobId").value("33333333-3333-4333-8333-333333333333"))
                .andExpect(jsonPath("$.page.number").value(2)).andExpect(jsonPath("$.page.totalPages").value(3)).andExpect(jsonPath("$.page.hasNext").value(true));
    }

    @Test
    @DisplayName("재시도는 원본 작업 URL과 새 보완 텍스트만으로 새 작업을 접수한다")
    void retry_허용작업_새보완텍스트로접수한다() throws Exception {
        UUID jobId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        when(queryService.retryUrl(jobId)).thenReturn("https://www.youtube.com/watch?v=video-id");
        when(useCase.submitRetry("https://www.youtube.com/watch?v=video-id", "새 입력")).thenReturn(new AiExtractionJobView(jobId,"ADMIN","c","v","https://www.youtube.com/watch?v=v","QUEUED",null,null,"GOOGLE_GEMINI","gemini-3-flash-preview","P1","S1",0,OffsetDateTime.now(),null,null,false));
        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/retry", jobId).contentType(MediaType.APPLICATION_JSON).content("{\"supplementText\":\"새 입력\",\"reason\":\"누락 보완\"}"))
                .andExpect(status().isAccepted());
        verify(useCase).submitRetry("https://www.youtube.com/watch?v=video-id", "새 입력");
    }

    @Test
    @DisplayName("검토 효과 경계가 없으면 409을 반환한다")
    void review_효과경계없음_409을반환한다() throws Exception {
        UUID jobId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        doThrow(new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_REVIEW_EFFECT_UNAVAILABLE", "effect unavailable"))
                .when(queryService).review(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.eq("ROLLBACK"), org.mockito.ArgumentMatchers.eq("AUTO_CONFIRMED"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("오등록"), org.mockito.ArgumentMatchers.anyList());
        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/review", jobId)
                        .principal(new UsernamePasswordAuthenticationToken("55555555-5555-4555-8555-555555555556", "n/a"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ROLLBACK\",\"expectedReviewStatus\":\"AUTO_CONFIRMED\",\"reason\":\"오등록\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AIEXTRACT_REVIEW_EFFECT_UNAVAILABLE"));
    }
}
