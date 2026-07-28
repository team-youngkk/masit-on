package com.masiton.restaurant.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantsCommand;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchCriteria;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryResult;
import com.masiton.restaurant.application.port.out.RestaurantSearchRow;
import com.masiton.restaurant.application.port.out.VisitedByRow;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("맛집 검색 Application 서비스")
class RestaurantSearchQueryServiceTest {

    private final RegionRepositoryPort regionRepositoryPort = mock(RegionRepositoryPort.class);
    private final FoodCategoryRepositoryPort foodCategoryRepositoryPort = mock(FoodCategoryRepositoryPort.class);
    private final RestaurantSearchQueryPort restaurantSearchQueryPort = mock(RestaurantSearchQueryPort.class);

    private final RestaurantSearchQueryService service = new RestaurantSearchQueryService(
            regionRepositoryPort, foodCategoryRepositoryPort, restaurantSearchQueryPort);

    @Test
    @DisplayName("검색어가 공백뿐이면 트림 후 이름 조건을 적용하지 않는다")
    void search_검색어공백뿐_이름조건미적용() {
        // given
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand("   ", null, null, null, 1, 20));

        // then
        verify(restaurantSearchQueryPort).search(argThatCriteria(criteria -> criteria.normalizedQuery() == null));
    }

    @Test
    @DisplayName("검색어가 앞뒤 공백을 포함하면 트림한 값으로 조건을 적용한다")
    void search_검색어앞뒤공백포함_트림한값으로조건적용() {
        // given
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand("  식당  ", null, null, null, 1, 20));

        // then
        verify(restaurantSearchQueryPort).search(argThatCriteria(criteria -> "식당".equals(criteria.normalizedQuery())));
    }

    @Test
    @DisplayName("검색어가 100자를 초과하면 400 INVALID_FIELD_VALUE(query)를 던진다")
    void search_검색어100자초과_400INVALID_FIELD_VALUE를던진다() {
        // given
        String tooLong = "가".repeat(101);

        // when & then
        assertThatThrownBy(() -> service.search(new SearchRestaurantsCommand(tooLong, null, null, null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("query");
                });
    }

    @Test
    @DisplayName("등록되지 않은 자치구는 400 INVALID_FIELD_VALUE(district)를 던진다")
    void search_등록되지않은자치구_400INVALID_FIELD_VALUE를던진다() {
        // given
        when(regionRepositoryPort.findByName("없는구")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.search(new SearchRestaurantsCommand(null, "없는구", null, null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("district");
                });
    }

    @Test
    @DisplayName("등록되지 않은 카테고리는 400 INVALID_FIELD_VALUE(category)를 던진다")
    void search_등록되지않은카테고리_400INVALID_FIELD_VALUE를던진다() {
        // given
        when(foodCategoryRepositoryPort.findByName("없는음식")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.search(new SearchRestaurantsCommand(null, null, "없는음식", null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("category");
                });
    }

    @Test
    @DisplayName("creatorId 형식이 UUID가 아니면 400 INVALID_IDENTIFIER를 던진다")
    void search_creatorId형식오류_400INVALID_IDENTIFIER를던진다() {
        // when & then
        assertThatThrownBy(() -> service.search(new SearchRestaurantsCommand(null, null, null, "not-a-uuid", 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_IDENTIFIER.name());
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("creatorId");
                });
    }

    @Test
    @DisplayName("creatorId가 존재하지 않거나 공개되지 않으면 400 INVALID_FIELD_VALUE(creatorId)를 던진다")
    void search_creatorId비공개또는존재하지않음_400INVALID_FIELD_VALUE를던진다() {
        // given
        UUID creatorId = UUID.randomUUID();
        when(restaurantSearchQueryPort.existsPublicCreator(creatorId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                        service.search(new SearchRestaurantsCommand(null, null, null, creatorId.toString(), 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("creatorId");
                });
    }

    @Test
    @DisplayName("모든 조건을 지정하면 정규화된 값으로 조합된 Criteria를 Port에 전달한다")
    void search_모든조건지정_정규화된Criteria를Port에전달한다() {
        // given
        UUID regionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(regionRepositoryPort.findByName("마포구")).thenReturn(Optional.of(region(regionId, "마포구")));
        when(foodCategoryRepositoryPort.findByName("한식")).thenReturn(Optional.of(foodCategory(categoryId, "한식")));
        when(restaurantSearchQueryPort.existsPublicCreator(creatorId)).thenReturn(true);
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand(" 식당 ", "마포구", "한식", creatorId.toString(), 2, 10));

        // then
        verify(restaurantSearchQueryPort).search(eq(new RestaurantSearchCriteria(
                "식당", regionId, categoryId, creatorId, 2, 10)));
    }

    @Test
    @DisplayName("결과가 없으면 totalPages는 0이고 hasNext는 false다")
    void search_결과없음_totalPages0이고hasNextfalse() {
        // given
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        RestaurantSearchResult result = service.search(new SearchRestaurantsCommand(null, null, null, null, 1, 20));

        // then
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        verify(restaurantSearchQueryPort, never()).findVisitedByRestaurantIds(any());
    }

    @Test
    @DisplayName("방문 유튜버가 4명 이상이면 채널명 오름차순 상위 3명만 반환하고 나머지 수를 계산한다")
    void search_방문유튜버4명이상_상위3명과나머지수를계산한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        RestaurantSearchRow row = new RestaurantSearchRow(restaurantId, "맛집", "마포구", "한식");
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(row), 1));

        UUID creatorD = UUID.randomUUID();
        UUID creatorB = UUID.randomUUID();
        UUID creatorA = UUID.randomUUID();
        UUID creatorC = UUID.randomUUID();
        when(restaurantSearchQueryPort.findVisitedByRestaurantIds(List.of(restaurantId)))
                .thenReturn(List.of(
                        new VisitedByRow(restaurantId, creatorD, "D채널"),
                        new VisitedByRow(restaurantId, creatorB, "B채널"),
                        new VisitedByRow(restaurantId, creatorA, "A채널"),
                        new VisitedByRow(restaurantId, creatorC, "C채널")));

        // when
        RestaurantSearchResult result = service.search(new SearchRestaurantsCommand(null, null, null, null, 1, 20));

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).visitedBy())
                .extracting("channelName")
                .containsExactly("A채널", "B채널", "C채널");
        assertThat(result.items().get(0).remainingVisitedByCount()).isEqualTo(1);
    }

    private Region region(UUID id, String name) {
        return new Region(id, "CODE", name, (short) 1, true, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private FoodCategory foodCategory(UUID id, String name) {
        return new FoodCategory(id, "CODE", name, (short) 1, true, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private RestaurantSearchCriteria argThatCriteria(java.util.function.Predicate<RestaurantSearchCriteria> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
