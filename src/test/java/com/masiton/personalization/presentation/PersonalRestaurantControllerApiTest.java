package com.masiton.personalization.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.personalization.application.port.in.PersonalRestaurantItem;
import com.masiton.personalization.application.port.in.PersonalRestaurantPage;
import com.masiton.personalization.application.port.in.PersonalRestaurantUseCase;
import com.masiton.security.infrastructure.web.MemberPrivateCacheFilter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("개인 맛집 관리 Controller API")
class PersonalRestaurantControllerApiTest {

    private final PersonalRestaurantUseCase useCase = mock(PersonalRestaurantUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PersonalRestaurantController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new MemberPrivateCacheFilter())
            .build();
    private final UUID memberId = UUID.randomUUID();

    @Test
    @DisplayName("찜 목록은 계약된 중첩 응답과 private no-store 헤더를 반환한다")
    void getFavorites_회원요청_목록계약과캐시헤더반환() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-30T10:15:30+09:00");
        when(useCase.getFavorites(memberId, 1, 20)).thenReturn(new PersonalRestaurantPage(
                List.of(new PersonalRestaurantItem(
                        restaurantId, "맛있는 식당", "마포구", "한식", favoritedAt)),
                1, 20, 1, 1, false));

        mockMvc.perform(get("/api/me/favorites").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].restaurant.id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.items[0].restaurant.name").value("맛있는 식당"))
                .andExpect(jsonPath("$.items[0].favoritedAt").value(favoritedAt.toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    @DisplayName("허용하지 않는 페이지 크기는 오류 본문에도 private no-store 헤더를 반환한다")
    void getFavorites_허용하지않는크기_400과캐시헤더반환() throws Exception {
        mockMvc.perform(get("/api/me/favorites")
                        .principal(authentication())
                        .queryParam("size", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("찜 상태 조회는 회원 Principal의 식별자만 사용한다")
    void getFavorite_회원Principal_찜상태반환() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(useCase.isFavorite(memberId, restaurantId)).thenReturn(true);

        mockMvc.perform(get("/api/me/favorites/{restaurantId}", restaurantId)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.favorited").value(true));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                memberId.toString(), "N/A", List.of());
    }
}
