package com.masiton.restaurant.presentation;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.restaurant.application.port.in.RestaurantRegistrationUseCase;
import com.masiton.restaurant.application.port.in.SearchAdminPlaceCandidatesUseCase;
import com.masiton.common.security.LegacyAdminActorResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("맛집 등록 Controller API")
class RestaurantRegistrationControllerApiTest {

    private final RestaurantRegistrationUseCase restaurantRegistrationUseCase = mock(RestaurantRegistrationUseCase.class);
    private final SearchAdminPlaceCandidatesUseCase searchAdminPlaceCandidatesUseCase =
            mock(SearchAdminPlaceCandidatesUseCase.class);
    private final LegacyAdminActorResolver legacyAdminActorResolver = mock(LegacyAdminActorResolver.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new RestaurantRegistrationController(restaurantRegistrationUseCase, searchAdminPlaceCandidatesUseCase,
                    legacyAdminActorResolver))
            .build();
    private final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("생성 성공 시 생성된 맛집과 Location을 반환한다")
    void create_신규맛집_201과Location을반환한다() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRegistrationUseCase.create(any())).thenReturn(
                new RestaurantRegistrationUseCase.RestaurantCreationResult(
                        new RestaurantRegistrationUseCase.RestaurantCandidate(
                                restaurantId, "맛집", "마포구", "한식", "서울특별시 마포구 월드컵로 1", null,
                                "02-000-0000", "https://place.map.kakao.com/1"),
                        true,
                        false));

        mockMvc.perform(post("/api/admin/restaurants")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationToken\":\"opaque-token\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/restaurants/" + restaurantId))
                .andExpect(jsonPath("$.id").value(restaurantId.toString()));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        when(legacyAdminActorResolver.resolve(adminId)).thenReturn(adminId);
        return UsernamePasswordAuthenticationToken.authenticated(adminId.toString(), "N/A", java.util.List.of());
    }
}
