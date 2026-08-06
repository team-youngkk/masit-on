package com.masiton.personal.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionDetail;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.RestaurantItem;
import com.masiton.security.infrastructure.web.MemberPrivateCacheFilter;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("개인 컬렉션 Controller API")
class PersonalCollectionControllerApiTest {

    private final PersonalCollectionUseCase useCase = mock(PersonalCollectionUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PersonalCollectionController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new MemberPrivateCacheFilter()).build();
    private final UUID memberId = UUID.randomUUID();

    @Test
    @DisplayName("생성은 멱등성 키와 현재 회원을 전달하고 201 응답을 재생한다")
    void create_유효한요청_201과privateCache를반환한다() throws Exception {
        UUID collectionId = UUID.randomUUID();
        when(useCase.create(memberId, "opaque-key", "가족과 갈 곳"))
                .thenReturn(new PersonalCollectionUseCase.CreationResult(
                        "{\"collectionId\":\"" + collectionId + "\",\"name\":\"가족과 갈 곳\"}"));

        mockMvc.perform(post("/api/me/collections").principal(authentication())
                        .header("Idempotency-Key", "opaque-key")
                        .contentType("application/json").content("{\"name\":\"가족과 갈 곳\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.collectionId").value(collectionId.toString()));
    }

    @Test
    @DisplayName("상세는 공개 활성 맛집과 1-base 페이지 계약을 반환한다")
    void detail_컬렉션조회_고정페이지계약을반환한다() throws Exception {
        UUID collectionId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:00:00Z");
        when(useCase.getCollection(memberId, collectionId, 1, 20)).thenReturn(
                new CollectionDetail(collectionId, "저녁", 1, now,
                        List.of(new RestaurantItem(restaurantId, "맛집", "서울", now)),
                        1, 20, 1, 1, false));

        mockMvc.perform(get("/api/me/collections/{id}", collectionId).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.restaurantCount").value(1));
    }

    @Test
    @DisplayName("이름 변경은 Command 완료 후 별도 조회로 최신 요약을 반환한다")
    void rename_유효한요청_write후조회로요약을반환한다() throws Exception {
        UUID collectionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-03T10:00:00Z");
        when(useCase.getSummary(memberId, collectionId)).thenReturn(
                new PersonalCollectionUseCase.CollectionSummary(collectionId, "다시 갈 곳", 1, now, now));

        mockMvc.perform(patch("/api/me/collections/{id}", collectionId).principal(authentication())
                        .contentType("application/json").content("{\"name\":\"다시 갈 곳\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("다시 갈 곳"))
                .andExpect(jsonPath("$.restaurantCount").value(1));

        var ordered = inOrder(useCase);
        ordered.verify(useCase).rename(memberId, collectionId, "다시 갈 곳");
        ordered.verify(useCase).getSummary(memberId, collectionId);
    }

    @Test
    @DisplayName("삭제는 대상 존재와 무관하게 204로 수렴한다")
    void delete_없는대상도_204를반환한다() throws Exception {
        UUID collectionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/me/collections/{id}", collectionId).principal(authentication()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "private, no-store"));

        verify(useCase).delete(memberId, collectionId);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(memberId.toString(), "N/A", List.of());
    }
}
