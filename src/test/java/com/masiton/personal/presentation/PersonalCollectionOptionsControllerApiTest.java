package com.masiton.personal.presentation;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.CollectionOption.AdditionStatus;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase;
import com.masiton.security.infrastructure.web.MemberPrivateCacheFilter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("개인 컬렉션 추가 옵션 Controller API")
class PersonalCollectionOptionsControllerApiTest {

    private final PersonalCollectionUseCase useCase = mock(PersonalCollectionUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PersonalCollectionOptionsController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new MemberPrivateCacheFilter()).build();
    private final UUID memberId = UUID.randomUUID();

    @Test
    @DisplayName("컬렉션별 공개 맛집 개수와 추가 상태를 반환한다")
    void getCollectionOptions_유효한요청_옵션목록을반환한다() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(useCase.getCollectionOptions(memberId, restaurantId)).thenReturn(List.of(
                new CollectionOption(collectionId, "가고 싶은 곳", 2,
                        AdditionStatus.ALREADY_INCLUDED)));

        mockMvc.perform(get("/api/me/collection-options").principal(authentication())
                        .queryParam("restaurantId", restaurantId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].collectionId").value(collectionId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("가고 싶은 곳"))
                .andExpect(jsonPath("$.items[0].restaurantCount").value(2))
                .andExpect(jsonPath("$.items[0].additionStatus").value("ALREADY_INCLUDED"))
                .andExpect(jsonPath("$.items[0].actualRestaurantCount").doesNotExist());
    }

    @Test
    @DisplayName("비공개 맛집은 404 RESTAURANT_NOT_FOUND를 반환한다")
    void getCollectionOptions_비공개맛집_404를반환한다() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(useCase.getCollectionOptions(memberId, restaurantId)).thenThrow(
                new BusinessException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND",
                        "요청한 맛집을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/me/collection-options").principal(authentication())
                        .queryParam("restaurantId", restaurantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("restaurantId는 한 번만 지정해야 한다")
    void getCollectionOptions_restaurantId중복_INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/me/collection-options").principal(authentication())
                        .queryParam("restaurantId", UUID.randomUUID().toString(),
                                UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                memberId.toString(), "N/A", List.of());
    }
}
