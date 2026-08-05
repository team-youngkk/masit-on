package com.masiton.participation.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.participation.application.AdminParticipationView;
import com.masiton.participation.application.ParticipationException;
import com.masiton.participation.application.port.in.AdminParticipationUseCase;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("관리자 제보·신고 Controller API")
class AdminParticipationControllerApiTest {

    private final AdminParticipationUseCase useCase = mock(AdminParticipationUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminParticipationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("목록은 필터와 1-base 페이지 메타데이터를 반환한다")
    void 목록_필터와페이지_오래된순항목을반환한다() throws Exception {
        AdminParticipationView.Submission item = submission(ParticipationStatus.RECEIVED);
        given(useCase.getSubmissions(ParticipationStatus.RECEIVED,
                ParticipationTargetType.RESTAURANT, 1, 20))
                .willReturn(new AdminParticipationView.Page<>(List.of(item), 1, 20, 1));

        mockMvc.perform(get("/api/admin/submissions").queryParam("status", "RECEIVED")
                        .queryParam("targetType", "RESTAURANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].memberId").value(item.memberId().toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("형식이 잘못된 식별자는 400, 유효한 미존재 식별자는 기능별 404다")
    void 상세_식별자형식과미존재_400과404를구분한다() throws Exception {
        UUID missing = UUID.randomUUID();
        given(useCase.getSubmission(missing)).willThrow(new ParticipationException(
                org.springframework.http.HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "없음"));

        mockMvc.perform(get("/api/admin/submissions/not-an-id"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"));
        mockMvc.perform(get("/api/admin/submissions/{id}", missing))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("상태 변경은 인증 관리자 ID와 서버 traceId를 전달한다")
    void 상태변경_관리자와추적식별자_서비스에전달한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(useCase.updateSubmission(any(), any(), any(), any())).willReturn(submission(ParticipationStatus.IN_REVIEW));

        mockMvc.perform(put("/api/admin/submissions/{id}/status", requestId)
                        .principal(new UsernamePasswordAuthenticationToken(adminId.toString(), "", List.of()))
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace")
                        .contentType("application/json").content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));

        ArgumentCaptor<UUID> actor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> trace = ArgumentCaptor.forClass(String.class);
        verify(useCase).updateSubmission(org.mockito.ArgumentMatchers.eq(requestId), actor.capture(), any(), trace.capture());
        assertThat(actor.getValue()).isEqualTo(adminId);
        assertThat(trace.getValue()).isEqualTo("server-trace");
    }

    private AdminParticipationView.Submission submission(ParticipationStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T12:00:00Z");
        return new AdminParticipationView.Submission(UUID.randomUUID(), UUID.randomUUID(),
                ParticipationTargetType.RESTAURANT, Map.of("name", "후보"), "충분한 설명입니다",
                null, status, null, null, null, now, now, List.of());
    }
}
