package com.masiton.restaurant.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.restaurant.application.port.in.RestaurantPlaceRevalidationUseCase;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.common.observability.TraceIdFilter;

@DisplayName("Kakao 장소 재검증 관리자 API")
class AdminRestaurantPlaceRevalidationControllerApiTest {

    private final RestaurantPlaceRevalidationUseCase useCase = mock();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AdminRestaurantPlaceRevalidationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    @DisplayName("수동 재검증은 202와 판정 결과를 반환한다")
    void 수동재검증_202와판정결과() throws Exception {
        when(useCase.run(restaurantId)).thenReturn(Optional.of(
                new RestaurantPlaceRevalidationUseCase.Result(restaurantId, "AUTO_CORRECTED", null)));

        mockMvc.perform(post("/api/admin/restaurants/{id}/place-revalidation", restaurantId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("AUTO_CORRECTED"));
    }

    @Test
    @DisplayName("stale 결과는 공통 오류 envelope의 409로 반환한다")
    void stale결과_공통오류로반환() throws Exception {
        when(useCase.run(restaurantId)).thenReturn(Optional.of(
                new RestaurantPlaceRevalidationUseCase.Result(restaurantId, "STALE_DISCARDED", null)));

        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "test-trace-id");
        try {
            mockMvc.perform(post("/api/admin/restaurants/{id}/place-revalidation", restaurantId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESTAURANT_PLACE_REVALIDATION_STALE"))
                    .andExpect(jsonPath("$.traceId").value("test-trace-id"));
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID_MDC_KEY);
        }
    }

    @Test
    @DisplayName("감사 조회는 이전·관측·적용 값을 반환한다")
    void 감사조회_변경값반환() throws Exception {
        OffsetDateTime checkedAt = OffsetDateTime.parse("2026-09-18T00:00:00Z");
        when(useCase.audits(restaurantId, 20)).thenReturn(List.of(new RestaurantPlaceRevalidationStore.Audit(
                RestaurantPlaceRevalidationStore.Outcome.AUTO_CORRECTED,
                Map.of("name", "새 맛집"), Map.of("name", "기존 맛집"), Map.of("name", "새 맛집"),
                "SAFE_FIELDS_CHANGED", null, checkedAt, null)));

        mockMvc.perform(get("/api/admin/restaurants/{id}/place-revalidation/audits", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("AUTO_CORRECTED"))
                .andExpect(jsonPath("$.items[0].previousValues.name").value("기존 맛집"))
                .andExpect(jsonPath("$.items[0].appliedValues.name").value("새 맛집"));
    }
}
