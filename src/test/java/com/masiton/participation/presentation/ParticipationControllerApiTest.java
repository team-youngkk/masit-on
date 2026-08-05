package com.masiton.participation.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.idempotency.application.IdempotencyExecutionResult;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.participation.application.ParticipationException;
import com.masiton.participation.application.ParticipationView;
import com.masiton.participation.application.port.in.ParticipationUseCase;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.security.infrastructure.web.MemberPrivateCacheFilter;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("회원 제보·신고 Controller API")
class ParticipationControllerApiTest {

    private final ParticipationUseCase useCase = mock(ParticipationUseCase.class);
    private final IdempotentCreationUseCase idempotency = mock(IdempotentCreationUseCase.class);
    private final UUID memberId = UUID.randomUUID();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ParticipationController(useCase, idempotency, new ObjectMapper()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new MemberPrivateCacheFilter())
            .build();

    @BeforeEach
    void executeCreationAction() {
        given(idempotency.execute(any(), any())).willAnswer(invocation -> {
            IdempotentCreationUseCase.CreationAction action = invocation.getArgument(1);
            return IdempotencyExecutionResult.created(action.create());
        });
    }

    @Test
    @DisplayName("제보 접수는 201과 회원 private cache 계약을 반환한다")
    void 제보접수_정상요청_201응답한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T12:00:00+09:00");
        given(useCase.createSubmission(any(), any())).willReturn(new ParticipationView.Submission(
                requestId, ParticipationTargetType.RESTAURANT,
                Map.of("name", "새 맛집", "roadAddress", "서울특별시 테스트로 1"),
                "새로운 맛집 등록을 제안합니다.", "https://example.com/evidence",
                ParticipationStatus.RECEIVED, null, now, now));

        mockMvc.perform(post("/api/me/submissions")
                        .principal(authentication())
                        .header("Idempotency-Key", "opaque-key-1234")
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "candidate":{"name":"새 맛집","roadAddress":"서울특별시 테스트로 1"},
                                  "description":"새로운 맛집 등록을 제안합니다.",
                                  "evidenceUrl":"https://example.com/evidence"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    @DisplayName("내 제보 목록은 계약된 페이지 객체와 관리자 필드 없는 항목을 반환한다")
    void 제보목록_회원조회_페이지계약을반환한다() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T12:00:00+09:00");
        ParticipationView.Submission item = new ParticipationView.Submission(
                UUID.randomUUID(), ParticipationTargetType.VIDEO,
                Map.of("videoUrl", "https://youtube.com/watch?v=test"),
                "새 영상 등록을 제안합니다.", null, ParticipationStatus.IN_REVIEW,
                null, now, now);
        given(useCase.getSubmissions(memberId, ParticipationStatus.IN_REVIEW, 1, 20))
                .willReturn(new ParticipationView.Page<>(List.of(item), 1, 20, 1));

        mockMvc.perform(get("/api/me/submissions")
                        .principal(authentication()).queryParam("status", "IN_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.items[0].internalNote").doesNotExist())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("멱등 키가 없거나 잘못된 페이지 크기는 공통 400 계약을 반환한다")
    void 잘못된요청_멱등키와페이지크기_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/me/submissions")
                        .principal(authentication()).contentType("application/json")
                        .content("""
                                {"targetType":"RESTAURANT","candidate":{},"description":"충분히 긴 설명입니다."}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));

        mockMvc.perform(get("/api/me/reports").principal(authentication()).queryParam("size", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("상세 경로의 형식이 잘못된 식별자는 404 식별자 없음 계약을 반환한다")
    void 상세조회_잘못된식별자_404를반환한다() throws Exception {
        mockMvc.perform(get("/api/me/submissions/not-an-id").principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));

        mockMvc.perform(get("/api/me/reports/not-an-id").principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("상세 경로의 유효한 식별자에 자원이 없으면 기능별 404를 반환한다")
    void 상세조회_유효한식별자에자원없음_기능404를반환한다() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        given(useCase.getSubmission(memberId, submissionId)).willThrow(new ParticipationException(
                HttpStatus.NOT_FOUND,
                "SUBMISSION_NOT_FOUND",
                "제보를 찾을 수 없습니다."));
        given(useCase.getReport(memberId, reportId)).willThrow(new ParticipationException(
                HttpStatus.NOT_FOUND,
                "REPORT_NOT_FOUND",
                "신고를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/me/submissions/{requestId}", submissionId).principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));

        mockMvc.perform(get("/api/me/reports/{requestId}", reportId).principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("멱등 요청 해시는 JSON null의 타입과 중첩 객체 키 순서를 보존한다")
    void 접수_멱등본문해시_null타입과중첩키순서를구분한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T12:00:00+09:00");
        given(useCase.createSubmission(any(), any())).willReturn(new ParticipationView.Submission(
                requestId, ParticipationTargetType.RESTAURANT, Map.of("name", "새 맛집"),
                "새로운 맛집 등록을 제안합니다.", null,
                ParticipationStatus.RECEIVED, null, now, now));

        postSubmission("hash-key-0001", "null", "{\"outer\":{\"b\":2,\"a\":1}}");
        postSubmission("hash-key-0002", "\"null\"", "{\"outer\":{\"a\":1,\"b\":2}}");

        ArgumentCaptor<IdempotencyRequest> requests = ArgumentCaptor.forClass(IdempotencyRequest.class);
        verify(idempotency, times(2)).execute(requests.capture(), any());
        assertThat(requests.getAllValues().get(0).requestHash())
                .isNotEqualTo(requests.getAllValues().get(1).requestHash());

        postSubmission("hash-key-0003", "null", "{\"outer\":{\"b\":2,\"a\":1}}");
        postSubmission("hash-key-0004", "null", "{\"outer\":{\"a\":1,\"b\":2}}");
        verify(idempotency, times(4)).execute(requests.capture(), any());
        assertThat(requests.getAllValues().get(4).requestHash())
                .isEqualTo(requests.getAllValues().get(5).requestHash());
    }

    private void postSubmission(String key, String evidenceUrl, String candidate) throws Exception {
        mockMvc.perform(post("/api/me/submissions")
                        .principal(authentication())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("""
                                {"targetType":"RESTAURANT","candidate":%s,
                                 "description":"새로운 맛집 등록을 제안합니다.","evidenceUrl":%s}
                                """.formatted(candidate, evidenceUrl)))
                .andExpect(status().isCreated());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(memberId.toString(), "N/A", List.of());
    }
}
