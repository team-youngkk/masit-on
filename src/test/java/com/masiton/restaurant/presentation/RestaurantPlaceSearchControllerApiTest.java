package com.masiton.restaurant.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.restaurant.application.port.in.RestaurantRegistrationUseCase;
import com.masiton.restaurant.application.port.in.SearchAdminPlaceCandidatesUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("관리자 장소 검색 Controller API")
class RestaurantPlaceSearchControllerApiTest {

    private final RestaurantRegistrationUseCase restaurantRegistrationUseCase = mock(RestaurantRegistrationUseCase.class);
    private final SearchAdminPlaceCandidatesUseCase searchAdminPlaceCandidatesUseCase =
            mock(SearchAdminPlaceCandidatesUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new RestaurantRegistrationController(restaurantRegistrationUseCase, searchAdminPlaceCandidatesUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("정상 검색은 200과 후보 목록을 반환한다")
    void 검색_정상요청_200과후보목록을반환한다() throws Exception {
        when(searchAdminPlaceCandidatesUseCase.search(any())).thenReturn(java.util.List.of(
                new SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult(
                        "아코", "https://place.map.kakao.com/example",
                        "서울특별시 강동구 성내동 12-38", "02-000-0000", "강동구")));

        mockMvc.perform(post("/api/admin/restaurant-place-searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"아코\",\"roadAddressHint\":\"서울 강동구 성내동 12-38\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].placeName").value("아코"))
                .andExpect(jsonPath("$.items[0].kakaoPlaceUrl").value("https://place.map.kakao.com/example"))
                .andExpect(jsonPath("$.items[0].roadAddress").value("서울특별시 강동구 성내동 12-38"))
                .andExpect(jsonPath("$.items[0].phoneNumber").value("02-000-0000"))
                .andExpect(jsonPath("$.items[0].district").value("강동구"));
    }

    @Test
    @DisplayName("후보가 없으면 200과 빈 items를 반환한다")
    void 검색_후보없음_200과빈items를반환한다() throws Exception {
        when(searchAdminPlaceCandidatesUseCase.search(any())).thenReturn(java.util.List.of());

        mockMvc.perform(post("/api/admin/restaurant-place-searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"없는가게\",\"roadAddressHint\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("name이 누락되면 400 MISSING_REQUIRED_FIELD를 반환한다")
    void 검색_name누락_400을반환한다() throws Exception {
        when(searchAdminPlaceCandidatesUseCase.search(any()))
                .thenThrow(new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "name is required."));

        mockMvc.perform(post("/api/admin/restaurant-place-searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roadAddressHint\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("요청 본문이 null이면 400 INVALID_REQUEST를 반환한다")
    void 검색_요청본문null_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/restaurant-place-searches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("name 길이를 넘으면 400 INVALID_FIELD_VALUE를 반환한다")
    void 검색_name길이위반_400을반환한다() throws Exception {
        when(searchAdminPlaceCandidatesUseCase.search(any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "name is invalid."));

        mockMvc.perform(post("/api/admin/restaurant-place-searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("카카오 조회에 실패하면 502 EXTERNAL_SERVICE_ERROR를 반환한다")
    void 검색_카카오조회실패_502를반환한다() throws Exception {
        when(searchAdminPlaceCandidatesUseCase.search(any()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        mockMvc.perform(post("/api/admin/restaurant-place-searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"아코\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"));
    }
}
