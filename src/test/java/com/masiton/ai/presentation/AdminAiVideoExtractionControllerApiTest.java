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
import com.masiton.ai.application.RegistrationUnitCommandService;
import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.GlobalExceptionHandler;

import tools.jackson.databind.ObjectMapper;

@DisplayName("관리자 AI 영상 추출 Controller API")
class AdminAiVideoExtractionControllerApiTest {

    private final AiExtractionJobUseCase useCase = mock(AiExtractionJobUseCase.class);
    private final AdminAiExtractionQueryService queryService = mock(AdminAiExtractionQueryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AdminAiVideoExtractionController(useCase, queryService, objectMapper))
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
                "gemini-3.5-flash-lite",
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
                "gemini-3.5-flash-lite",
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
                java.util.List.of(new AiExtractionJobView(UUID.fromString("33333333-3333-4333-8333-333333333333"), "ADMIN", "c", "v", "https://www.youtube.com/watch?v=v", "FAILED", null, null, "GOOGLE_GEMINI", "gemini-3.5-flash-lite", "P1", "S1", 1, OffsetDateTime.parse("2026-08-11T00:00:00Z"), null, OffsetDateTime.parse("2026-08-11T00:01:00Z"), false)), 41));
        mockMvc.perform(get("/api/admin/ai/video-extractions?executionStatus=FAILED&source=ADMIN&page=2&size=20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].jobId").value("33333333-3333-4333-8333-333333333333"))
                .andExpect(jsonPath("$.page.number").value(2)).andExpect(jsonPath("$.page.totalPages").value(3)).andExpect(jsonPath("$.page.hasNext").value(true));
    }

    @Test
    @DisplayName("재시도는 원본 작업 URL과 새 보완 텍스트만으로 새 작업을 접수한다")
    void retry_허용작업_새보완텍스트로접수한다() throws Exception {
        UUID jobId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        when(queryService.retryUrl(jobId)).thenReturn("https://www.youtube.com/watch?v=video-id");
        when(useCase.submitRetry("https://www.youtube.com/watch?v=video-id", "새 입력", "누락 보완")).thenReturn(new AiExtractionJobView(jobId,"ADMIN","c","v","https://www.youtube.com/watch?v=v","QUEUED",null,null,"GOOGLE_GEMINI","gemini-3.5-flash-lite","P1","S1",0,OffsetDateTime.now(),null,null,false));
        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/retry", jobId).contentType(MediaType.APPLICATION_JSON).content("{\"supplementText\":\"새 입력\",\"reason\":\"누락 보완\"}"))
                .andExpect(status().isAccepted());
        verify(useCase).submitRetry("https://www.youtube.com/watch?v=video-id", "새 입력", "누락 보완");
    }

    @Test
    @DisplayName("같은 필드에 복수 후보가 남으면 상세 조회 응답에 후보가 전부 노출된다")
    void detail_같은필드복수후보_상세조회응답에후보가전부노출된다() throws Exception {
        UUID jobId = UUID.fromString("66666666-6666-4666-8666-666666666666");
        AiExtractionJobView job = new AiExtractionJobView(jobId, "ADMIN", "channel-id", "video-id",
                "https://www.youtube.com/watch?v=video-id", "SUCCEEDED", "COMPLETE", "AUTO_BLOCKED",
                "GOOGLE_GEMINI", "gemini-3.5-flash-lite", "P1", "S1", 1,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"), OffsetDateTime.parse("2026-08-11T00:01:00Z"),
                OffsetDateTime.parse("2026-08-11T00:02:00Z"), false);
        AiExtractionAdminQueryPort.Detail detail = new AiExtractionAdminQueryPort.Detail(job,
                objectMapper.readTree("""
                        {
                          "restaurantName": [
                            {"value":"첫 맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                            {"value":"둘째 맛집","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}}
                          ],
                          "address": "서울시"
                        }
                        """),
                objectMapper.createArrayNode(),
                objectMapper.readTree("""
                        {"address": 0.90}
                        """),
                objectMapper.readTree("""
                        {"address": {"type":"TIMESTAMP","startMs":10,"endMs":20}}
                        """),
                objectMapper.createArrayNode(), false, null, null, java.util.List.of());
        when(queryService.detail(jobId)).thenReturn(new AdminAiExtractionQueryService.AdminJobDetail(
                detail, java.util.List.of(), "AUTO_BLOCKED"));

        mockMvc.perform(get("/api/admin/ai/video-extractions/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(3))
                .andExpect(jsonPath("$.candidates[0].field").value("restaurantName"))
                .andExpect(jsonPath("$.candidates[0].value").value("첫 맛집"))
                .andExpect(jsonPath("$.candidates[1].field").value("restaurantName"))
                .andExpect(jsonPath("$.candidates[1].value").value("둘째 맛집"))
                .andExpect(jsonPath("$.candidates[2].field").value("address"))
                .andExpect(jsonPath("$.candidates[2].value").value("서울시"));
    }

    @Test
    @DisplayName("보충 검증 실패는 기존 차단 사유와 이번 실패 사유를 함께 반환한다")
    void review_보충검증실패_기존사유와이번실패사유를함께반환한다() throws Exception {
        UUID jobId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        doThrow(new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "AIEXTRACT_VALIDATION_CONFLICT",
                "Registration unit validation conflict.",
                new RegistrationUnitCommandService.ValidationConflictDetails(
                        "PLACE_AMBIGUOUS", "VISIT_EVIDENCE_REQUIRED",
                        java.util.List.of("SUPPLEMENT", "MANUAL_REGISTRATION"),
                        java.util.List.of("kakaoPlaceUrl"))))
                .when(queryService).review(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.eq("CONFIRM"),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("보충 사유"),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/review", jobId)
                        .principal(new UsernamePasswordAuthenticationToken("55555555-5555-4555-8555-555555555556", "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRM\",\"unitId\":\"77777777-7777-4777-8777-777777777777\","
                                + "\"expectedReviewStatus\":\"AUTO_BLOCKED\",\"reason\":\"보충 사유\","
                                + "\"supplements\":{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.blockReason").value("PLACE_AMBIGUOUS"))
                .andExpect(jsonPath("$.details.validationFailureReason").value("VISIT_EVIDENCE_REQUIRED"))
                .andExpect(jsonPath("$.details.requiredSupplements[0]").value("kakaoPlaceUrl"));
    }

    @Test
    @DisplayName("검토 효과 경계가 없으면 409을 반환한다")
    void review_효과경계없음_409을반환한다() throws Exception {
        UUID jobId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        doThrow(new BusinessException(HttpStatus.CONFLICT, "AIEXTRACT_DUPLICATE_CONFLICT", "effect unavailable"))
                .when(queryService).review(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.eq("ROLLBACK"),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("오등록"),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/review", jobId)
                        .principal(new UsernamePasswordAuthenticationToken("55555555-5555-4555-8555-555555555556", "n/a"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ROLLBACK\",\"unitId\":\"77777777-7777-4777-8777-777777777777\",\"expectedReviewStatus\":\"AUTO_CONFIRMED\",\"reason\":\"오등록\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AIEXTRACT_DUPLICATE_CONFLICT"));
    }

    @Test
    @DisplayName("AUTO_BLOCKED 등록 단위 일괄 폐기는 200과 폐기된 unitId 목록을 반환한다")
    void discardAllBlocked_정상요청은_200과폐기된unitId목록을반환한다() throws Exception {
        UUID jobId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        UUID memberAccountId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        UUID firstUnitId = UUID.fromString("77777777-7777-4777-8777-777777777777");
        UUID secondUnitId = UUID.fromString("99999999-9999-4999-8999-999999999999");
        when(queryService.discardAllBlocked(org.mockito.ArgumentMatchers.eq(jobId),
                org.mockito.ArgumentMatchers.eq("여러 건 동시 처리 사유"), org.mockito.ArgumentMatchers.eq(memberAccountId)))
                .thenReturn(java.util.List.of(firstUnitId, secondUnitId));

        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/registration-units/discard-all", jobId)
                        .principal(new UsernamePasswordAuthenticationToken(memberAccountId.toString(), "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"여러 건 동시 처리 사유\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discardedCount").value(2))
                .andExpect(jsonPath("$.discardedUnitIds.length()").value(2))
                .andExpect(jsonPath("$.discardedUnitIds[0]").value(firstUnitId.toString()))
                .andExpect(jsonPath("$.discardedUnitIds[1]").value(secondUnitId.toString()));
    }

    @Test
    @DisplayName("AUTO_BLOCKED 등록 단위 일괄 폐기는 reason 누락 시 400 MISSING_REQUIRED_FIELD를 반환한다")
    void discardAllBlocked_reason누락_400필수값오류를반환한다() throws Exception {
        UUID jobId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        UUID memberAccountId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        doThrow(new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "reason",
                "reason is required and must be at most 1,000 characters."))
                .when(queryService).discardAllBlocked(org.mockito.ArgumentMatchers.eq(jobId),
                        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(memberAccountId));

        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/registration-units/discard-all", jobId)
                        .principal(new UsernamePasswordAuthenticationToken(memberAccountId.toString(), "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("review는 JWT principal의 member_account.id를 변환 없이 그대로 adminId로 전달한다")
    void review_principal의memberAccountId를변환없이그대로전달한다() throws Exception {
        UUID jobId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        UUID memberAccountId = UUID.fromString("88888888-8888-4888-8888-888888888888");

        mockMvc.perform(post("/api/admin/ai/video-extractions/{jobId}/review", jobId)
                        .principal(new UsernamePasswordAuthenticationToken(memberAccountId.toString(), "n/a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DISCARD\",\"unitId\":\"77777777-7777-4777-8777-777777777777\","
                                + "\"reason\":\"근거 부족\"}"))
                .andExpect(status().isNoContent());

        verify(queryService).review(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.eq("DISCARD"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("근거 부족"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(memberAccountId));
    }
}
