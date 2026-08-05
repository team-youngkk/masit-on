package com.masiton.curation.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.curation.application.CurationException;
import com.masiton.curation.application.port.in.PublicCurationUseCase;
import com.masiton.curation.application.port.in.PublicCurationUseCase.PublicCuration;
import com.masiton.curation.application.port.in.PublicCurationUseCase.RestaurantItem;

@DisplayName("공개 큐레이션 Controller API")
class PublicCurationControllerApiTest {

    private final PublicCurationUseCase useCase = mock(PublicCurationUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublicCurationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @BeforeEach
    void setUpTraceId() {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "server-trace");
    }

    @AfterEach
    void clearTraceId() {
        MDC.clear();
    }

    @Test
    @DisplayName("목록은 items 래퍼와 공개 응답 필드를 반환한다")
    void 목록_공개응답계약_반환() throws Exception {
        UUID curationId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(useCase.getPublishedCurations()).thenReturn(List.of(curation(curationId, restaurantId)));

        mockMvc.perform(get("/api/curations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].curationId").value(curationId.toString()))
                .andExpect(jsonPath("$.items[0].items[0].restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.items[0].items[0].name").value("공개 맛집"))
                .andExpect(jsonPath("$.items[0].items[0].roadAddress").value("서울 테스트로 1"))
                .andExpect(jsonPath("$.items[0].publishedAt").exists())
                .andExpect(jsonPath("$.items[0].updatedAt").exists());
    }

    @Test
    @DisplayName("상세는 래퍼 없이 객체를 반환하고 숨김 결과는 빈 items를 유지한다")
    void 상세_직접객체_빈구성반환() throws Exception {
        UUID curationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T00:00:00Z");
        when(useCase.getPublishedCuration(curationId)).thenReturn(
                new PublicCuration(curationId, "제목", "설명", List.of(), now, now));

        mockMvc.perform(get("/api/curations/{curationId}", curationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curationId").value(curationId.toString()))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    @DisplayName("비게시 또는 없는 상세는 CURATION_NOT_FOUND와 traceId를 반환한다")
    void 상세_비게시또는없음_404계약() throws Exception {
        UUID curationId = UUID.randomUUID();
        when(useCase.getPublishedCuration(curationId)).thenThrow(CurationException.notFound());

        mockMvc.perform(get("/api/curations/{curationId}", curationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURATION_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("server-trace"));
    }

    @Test
    @DisplayName("형식이 잘못된 상세 식별자는 INVALID_IDENTIFIER를 반환한다")
    void 상세_잘못된식별자_400계약() throws Exception {
        mockMvc.perform(get("/api/curations/{curationId}", "not-an-identifier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value("server-trace"));
    }

    private PublicCuration curation(UUID curationId, UUID restaurantId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-05T00:00:00Z");
        return new PublicCuration(curationId, "제목", "설명",
                List.of(new RestaurantItem(restaurantId, "공개 맛집", "서울 테스트로 1")), now, now);
    }
}
