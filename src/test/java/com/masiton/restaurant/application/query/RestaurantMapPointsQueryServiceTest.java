package com.masiton.restaurant.application.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantMapPointsResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantMapPointsCommand;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.MapRateLimitPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.RestaurantMapPointRow;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsCriteria;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsQueryPort;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;
import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("지도 맛집 마커 조회 Application 서비스")
class RestaurantMapPointsQueryServiceTest {

    private final MapRateLimitPort mapRateLimitPort = mock(MapRateLimitPort.class);
    private final RegionRepositoryPort regionRepositoryPort = mock(RegionRepositoryPort.class);
    private final FoodCategoryRepositoryPort foodCategoryRepositoryPort = mock(FoodCategoryRepositoryPort.class);
    private final RestaurantMapPointsQueryPort restaurantMapPointsQueryPort = mock(RestaurantMapPointsQueryPort.class);
    private final FindDistinctValidRestaurantIdsByCreatorQuery findRestaurantIdsByCreatorQuery =
            mock(FindDistinctValidRestaurantIdsByCreatorQuery.class);

    private final RestaurantMapPointsQueryService service = new RestaurantMapPointsQueryService(
            mapRateLimitPort,
            regionRepositoryPort,
            foodCategoryRepositoryPort,
            restaurantMapPointsQueryPort,
            findRestaurantIdsByCreatorQuery);

    RestaurantMapPointsQueryServiceTest() {
        when(mapRateLimitPort.tryAcquire(any())).thenReturn(true);
        when(restaurantMapPointsQueryPort.findMatching(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("호출 제한을 초과하면 429 RATE_LIMIT_EXCEEDED를 던진다")
    void search_호출제한초과_429RATE_LIMIT_EXCEEDED를던진다() {
        // given
        when(mapRateLimitPort.tryAcquire("client-1")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.search(command("client-1")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
                    assertThat(exception.status().value()).isEqualTo(429);
                    assertThat(exception.retryAfterSeconds()).isEqualTo(1L);
                });
        verify(restaurantMapPointsQueryPort, never()).findMatching(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("결과가 201건이면 TOO_MANY_RESULTS와 빈 items를 반환한다")
    void search_결과201건_TOO_MANY_RESULTS와빈items를반환한다() {
        // given
        List<RestaurantMapPointRow> rows = java.util.stream.IntStream.range(0, 201)
                .mapToObj(index -> new RestaurantMapPointRow(
                        UUID.randomUUID(), "맛집" + index, "한식", "서울특별시", bd("37.5"), bd("127.0")))
                .toList();
        when(restaurantMapPointsQueryPort.findMatching(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(rows);

        // when
        RestaurantMapPointsResult result = service.search(command("client-1"));

        // then
        assertThat(result.resultStatus()).isEqualTo(RestaurantMapPointsResult.ResultStatus.TOO_MANY_RESULTS);
        assertThat(result.limit()).isEqualTo(200);
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("결과가 200건 이하면 AVAILABLE과 전체 결과를 반환한다")
    void search_결과200건이하_AVAILABLE과전체결과를반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        when(restaurantMapPointsQueryPort.findMatching(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new RestaurantMapPointRow(
                        restaurantId, "마포 맛집", "한식", "서울특별시 마포구 월드컵로 1", bd("37.5665"), bd("126.9780"))));

        // when
        RestaurantMapPointsResult result = service.search(command("client-1"));

        // then
        assertThat(result.resultStatus()).isEqualTo(RestaurantMapPointsResult.ResultStatus.AVAILABLE);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(restaurantId);
    }

    @Test
    @DisplayName("등록되지 않은 자치구는 400 INVALID_FIELD_VALUE(district)를 던진다")
    void search_등록되지않은자치구_400INVALID_FIELD_VALUE를던진다() {
        // given
        when(regionRepositoryPort.findByName("없는구")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.search(new SearchRestaurantMapPointsCommand(
                null, "없는구", null, null, "client-1")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
                    assertThat(exception.fieldErrors()).extracting("field").containsExactly("district");
                });
    }

    @Test
    @DisplayName("creatorId 형식이 UUID가 아니면 400 INVALID_IDENTIFIER를 던진다")
    void search_creatorId형식오류_400INVALID_IDENTIFIER를던진다() {
        assertThatThrownBy(() -> service.search(new SearchRestaurantMapPointsCommand(
                null, null, null, "not-a-uuid", "client-1")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_IDENTIFIER.name());
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
        UUID candidateId = UUID.randomUUID();
        when(regionRepositoryPort.findByName("마포구")).thenReturn(Optional.of(
                new Region(regionId, "CODE", "마포구", (short) 1, true, null, null)));
        when(foodCategoryRepositoryPort.findByName("한식")).thenReturn(Optional.of(
                new FoodCategory(categoryId, "CODE", "한식", (short) 1, true, null, null)));
        when(findRestaurantIdsByCreatorQuery.findDistinctValidRestaurantIdsByCreator(creatorId))
                .thenReturn(new CreatorRestaurantCandidates(true, Set.of(candidateId)));

        // when
        service.search(new SearchRestaurantMapPointsCommand(
                " 식당 ", "마포구", "한식", creatorId.toString(), "client-1"));

        // then
        verify(restaurantMapPointsQueryPort).findMatching(argThatCriteria(criteria ->
                "식당".equals(criteria.normalizedQuery())
                        && regionId.equals(criteria.regionId())
                        && categoryId.equals(criteria.foodCategoryId())
                        && Set.of(candidateId).equals(criteria.candidateRestaurantIds())),
                org.mockito.ArgumentMatchers.eq(201));
    }

    private SearchRestaurantMapPointsCommand command(String clientAddress) {
        return new SearchRestaurantMapPointsCommand(null, null, null, null, clientAddress);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private RestaurantMapPointsCriteria argThatCriteria(Predicate<RestaurantMapPointsCriteria> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
