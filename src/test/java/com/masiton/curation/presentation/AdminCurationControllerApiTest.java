package com.masiton.curation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.curation.application.CurationException;
import com.masiton.curation.application.port.in.AdminCurationUseCase;
import com.masiton.curation.application.port.in.AdminCurationUseCase.CurationDetail;
import com.masiton.curation.application.port.in.AdminCurationUseCase.CurationSummary;
import com.masiton.curation.domain.model.CurationStatus;

@DisplayName("관리자 큐레이션 Controller API")
class AdminCurationControllerApiTest {

    private final AdminCurationUseCase useCase = mock(AdminCurationUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminCurationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUpTraceId() {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "server-trace");
    }

    @AfterEach
    void clearTraceId() {
        MDC.clear();
    }

    @Test
    @DisplayName("목록은 상태 필터와 1-base 페이지 메타데이터를 반환한다")
    void 목록_게시상태필터_페이지반환() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T00:00:00Z");
        CurationSummary item = new CurationSummary(UUID.randomUUID(), "제목", "", CurationStatus.PUBLISHED,
                1, 2, true, now, now, now);
        given(useCase.getCurations(CurationStatus.PUBLISHED, 1, 20))
                .willReturn(new AdminCurationUseCase.Page<>(List.of(item), 1, 20, 1));

        mockMvc.perform(get("/api/admin/curations").queryParam("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].restaurantCount").value(2))
                .andExpect(jsonPath("$.items[0].hasHiddenRestaurants").value(true))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("편집은 인증 관리자 ID와 서버 traceId를 유스케이스에 전달한다")
    void 편집_관리자와추적식별자_전달() throws Exception {
        UUID curationId = UUID.randomUUID();
        given(useCase.updateContent(any(), any(), any(), any(), any())).willReturn(detail(curationId));

        mockMvc.perform(patch("/api/admin/curations/{id}", curationId)
                        .principal(new UsernamePasswordAuthenticationToken(adminId.toString(), "", List.of()))
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace")
                        .contentType("application/json").content("{\"title\":\" 새 제목 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curationId").value(curationId.toString()));

        ArgumentCaptor<UUID> actor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> trace = ArgumentCaptor.forClass(String.class);
        verify(useCase).updateContent(org.mockito.ArgumentMatchers.eq(curationId), actor.capture(),
                org.mockito.ArgumentMatchers.eq(" 새 제목 "), org.mockito.ArgumentMatchers.isNull(), trace.capture());
        assertThat(actor.getValue()).isEqualTo(adminId);
        assertThat(trace.getValue()).isEqualTo("server-trace");
    }

    @Test
    @DisplayName("기능별 없음 오류 코드와 traceId를 그대로 반환한다")
    void 상세_없음_큐레이션오류반환() throws Exception {
        UUID curationId = UUID.randomUUID();
        given(useCase.getCuration(curationId)).willThrow(CurationException.notFound());

        mockMvc.perform(get("/api/admin/curations/{id}", curationId)
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.code").value("CURATION_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("server-trace"));
    }

    @Test
    @DisplayName("생성은 멱등 키와 관리자를 전달하고 201 JSON을 반환한다")
    void 생성_멱등키와관리자_201반환() throws Exception {
        given(useCase.create(any(), any(), any(), any(), any()))
                .willReturn(new AdminCurationUseCase.CreationResult("{\"status\":\"DRAFT\"}"));

        mockMvc.perform(post("/api/admin/curations")
                        .principal(new UsernamePasswordAuthenticationToken(adminId.toString(), "", List.of()))
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace")
                        .header("Idempotency-Key", "curation-key-01")
                        .contentType("application/json").content("{\"title\":\"제목\",\"description\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("구성·게시·메인 순서 PUT은 각 전체 교체 유스케이스로 연결된다")
    void put경로_구성게시메인순서_유스케이스연결() throws Exception {
        UUID curationId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        given(useCase.replaceRestaurants(any(), any(), any(), any())).willReturn(detail(curationId));
        given(useCase.setPublication(any(), any(), any(), any())).willReturn(detail(curationId));
        given(useCase.replaceMainOrder(any(), any(), any())).willReturn(List.of());
        var principal = new UsernamePasswordAuthenticationToken(adminId.toString(), "", List.of());

        mockMvc.perform(put("/api/admin/curations/{id}/restaurants", curationId).principal(principal)
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace")
                        .contentType("application/json")
                        .content("{\"restaurantIds\":[\"" + restaurantId + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/curations/{id}/publication", curationId).principal(principal)
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace")
                        .contentType("application/json").content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/curations/main-order").principal(principal)
                        .requestAttr(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "server-trace")
                        .contentType("application/json").content("{\"curationIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());

        verify(useCase).replaceRestaurants(curationId, adminId, List.of(restaurantId), "server-trace");
        verify(useCase).setPublication(curationId, adminId, CurationStatus.PUBLISHED, "server-trace");
        verify(useCase).replaceMainOrder(adminId, List.of(), "server-trace");
    }

    private CurationDetail detail(UUID id) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T00:00:00Z");
        return new CurationDetail(id, "새 제목", "", CurationStatus.DRAFT, null,
                adminId, adminId, null, now, now, List.of());
    }
}
