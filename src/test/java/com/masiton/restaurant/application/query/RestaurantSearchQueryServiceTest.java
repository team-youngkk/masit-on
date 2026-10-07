package com.masiton.restaurant.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.RestaurantFilterOptions;
import com.masiton.restaurant.application.port.in.SearchRestaurantsCommand;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchCriteria;
import com.masiton.restaurant.application.port.out.RestaurantFilterOptionNames;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryResult;
import com.masiton.restaurant.application.port.out.RestaurantSearchRow;
import com.masiton.restaurant.application.port.out.VisitedByRow;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;
import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

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
    private final FindDistinctValidRestaurantIdsByCreatorQuery findRestaurantIdsByCreatorQuery =
            mock(FindDistinctValidRestaurantIdsByCreatorQuery.class);

    private final RestaurantSearchQueryService service = new RestaurantSearchQueryService(
            regionRepositoryPort,
            foodCategoryRepositoryPort,
            restaurantSearchQueryPort,
            findRestaurantIdsByCreatorQuery);

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
    @DisplayName("필터 선택지는 Query Port가 반환한 공개 맛집 사용값을 그대로 전달한다")
    void getFilterOptions_공개맛집사용값_그대로전달한다() {
        // given
        when(restaurantSearchQueryPort.findAvailableFilterOptions())
                .thenReturn(new RestaurantFilterOptionNames(
                        List.of("마포구", "강남구"), List.of("한식", "일식")));

        // when
        RestaurantFilterOptions result = service.getFilterOptions();

        // then
        assertThat(result.districts()).containsExactly("마포구", "강남구");
        assertThat(result.categories()).containsExactly("한식", "일식");
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
        when(findRestaurantIdsByCreatorQuery.findDistinctValidRestaurantIdsByCreator(creatorId))
                .thenReturn(new CreatorRestaurantCandidates(false, Set.of()));

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
        UUID candidateRestaurantId1 = UUID.randomUUID();
        UUID candidateRestaurantId2 = UUID.randomUUID();
        when(regionRepositoryPort.findByName("마포구")).thenReturn(Optional.of(region(regionId, "마포구")));
        when(foodCategoryRepositoryPort.findByName("한식")).thenReturn(Optional.of(foodCategory(categoryId, "한식")));
        when(findRestaurantIdsByCreatorQuery.findDistinctValidRestaurantIdsByCreator(creatorId))
                .thenReturn(new CreatorRestaurantCandidates(
                        true, Set.of(candidateRestaurantId1, candidateRestaurantId2)));
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand(" 식당 ", "마포구", "한식", creatorId.toString(), 2, 10));

        // then
        verify(restaurantSearchQueryPort).search(eq(new RestaurantSearchCriteria(
                "식당", regionId, categoryId, Set.of(candidateRestaurantId1, candidateRestaurantId2), 2, 10)));
    }

    @Test
    @DisplayName("태그 조건을 기존 목록 Query Criteria에 그대로 전달한다")
    void search_태그조건지정_Criteria에전달한다() {
        // given
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));
        when(restaurantSearchQueryPort.findActiveTagCodes(List.of("MENU_NAENGMYEON", "OCCASION_SOLO")))
                .thenReturn(Set.of("MENU_NAENGMYEON", "OCCASION_SOLO"));

        // when
        service.search(new SearchRestaurantsCommand(
                null, null, null, null, List.of("MENU_NAENGMYEON", "OCCASION_SOLO"), 1, 20));

        // then
        verify(restaurantSearchQueryPort).search(argThatCriteria(criteria ->
                criteria.tags().equals(Set.of("MENU_NAENGMYEON", "OCCASION_SOLO"))));
    }

    @Test
    @DisplayName("creatorId가 없으면 후보 제한 없음(null)을 Criteria에 전달한다")
    void search_creatorId없음_후보제한없음을전달한다() {
        // given
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand(null, null, null, null, 1, 20));

        // then
        verify(restaurantSearchQueryPort).search(
                argThatCriteria(criteria -> criteria.candidateRestaurantIds() == null));
        verify(findRestaurantIdsByCreatorQuery, never()).findDistinctValidRestaurantIdsByCreator(any());
    }

    @Test
    @DisplayName("공개 유튜버의 유효 방문 후보가 없으면 빈 후보 집합을 Criteria에 전달한다")
    void search_공개유튜버후보없음_빈후보집합을전달한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        when(findRestaurantIdsByCreatorQuery.findDistinctValidRestaurantIdsByCreator(creatorId))
                .thenReturn(new CreatorRestaurantCandidates(true, Set.of()));
        when(restaurantSearchQueryPort.search(any()))
                .thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand(null, null, null, creatorId.toString(), 1, 20));

        // then
        verify(restaurantSearchQueryPort).search(
                argThatCriteria(criteria -> Set.of().equals(criteria.candidateRestaurantIds())));
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
        RestaurantSearchRow row = new RestaurantSearchRow(restaurantId, "맛집", "마포구", "한식", null);
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

    @ParameterizedTest
    @ValueSource(strings = {"1100000000", "1168000000", "3611000000"})
    @DisplayName("시도·자치구·세종의 행정구역 코드를 내부 지역 ID로 변환한다")
    void search_활성행정구역코드_지역ID로조회한다(String code) {
        // given
        UUID id = UUID.randomUUID();
        when(regionRepositoryPort.findByAdministrativeCode(code)).thenReturn(Optional.of(region(id, "지역")));
        when(restaurantSearchQueryPort.search(any())).thenReturn(new RestaurantSearchQueryResult(List.of(), 0));

        // when
        service.search(new SearchRestaurantsCommand(null, null, null, null, List.of(), 1, 21, code));

        // then
        verify(restaurantSearchQueryPort).search(argThatCriteria(criteria -> id.equals(criteria.regionId())));
        verify(regionRepositoryPort, never()).findByName(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "110000000", "11000000000", "abcdefghij", "1100000000,2600000000",
            " 1100000000", "1100000000 "})
    @DisplayName("행정구역 코드는 공백 없는 ASCII 숫자 10자리만 허용한다")
    void search_행정구역코드형식오류_조회없이거부한다(String code) {
        // given
        SearchRestaurantsCommand command = new SearchRestaurantsCommand(
                null, null, null, null, List.of(), 1, 21, code);

        // when & then
        assertThatThrownBy(() -> service.search(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("INVALID_FIELD_VALUE");
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("regionCode");
                });
        verify(restaurantSearchQueryPort, never()).search(any());
        verify(regionRepositoryPort, never()).findByAdministrativeCode(any());
    }

    @Test
    @DisplayName("존재하지 않거나 비활성인 행정구역 코드는 조회 없이 거부한다")
    void search_미등록또는비활성지역_조회없이거부한다() {
        // given
        Region inactive = new Region(UUID.randomUUID(), "INACTIVE", "비활성", (short) 1, false,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(regionRepositoryPort.findByAdministrativeCode("1100000000")).thenReturn(Optional.of(inactive));

        // when & then
        for (String code : List.of("1100000000", "9999999999")) {
            assertThatThrownBy(() -> service.search(new SearchRestaurantsCommand(
                    null, null, null, null, List.of(), 1, 21, code)))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.fieldErrors()).extracting("field").containsExactly("regionCode"));
        }
        verify(restaurantSearchQueryPort, never()).search(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"마포구", "", " "})
    @DisplayName("district가 빈 값이어도 regionCode와 동시에 지정하면 거부한다")
    void validateFilters_지역조건동시지정_조회없이거부한다(String district) {
        // given
        SearchRestaurantsCommand command = new SearchRestaurantsCommand(
                null, district, null, null, List.of(), 1, 21, "1100000000");

        // when & then
        assertThatThrownBy(() -> service.validateFilters(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.fieldErrors()).extracting("field").containsExactly("regionCode"));
        verify(restaurantSearchQueryPort, never()).search(any());
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
