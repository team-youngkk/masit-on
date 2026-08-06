package com.masiton.restaurant.application.query;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.masiton.restaurant.application.port.in.PopularRestaurantSummary;
import com.masiton.restaurant.application.port.out.PopularRestaurantQueryPort;
import com.masiton.restaurant.application.port.out.PopularRestaurantRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("인기 맛집 조회 Application 서비스")
class PopularRestaurantQueryServiceTest {

    private final PopularRestaurantQueryPort popularRestaurantQueryPort = mock(PopularRestaurantQueryPort.class);

    private final PopularRestaurantQueryService service =
            new PopularRestaurantQueryService(popularRestaurantQueryPort);

    @Test
    @DisplayName("인기 맛집 조회는 Query Port에 최대 20건 제한을 전달한다")
    void findPopularRestaurants_조회시_Port에20건제한을전달한다() {
        // given
        when(popularRestaurantQueryPort.findTopByFavoriteCount(20)).thenReturn(List.of());

        // when
        service.findPopularRestaurants();

        // then
        verify(popularRestaurantQueryPort).findTopByFavoriteCount(20);
    }

    @Test
    @DisplayName("Port가 반환한 순서대로 1부터 연속된 순위를 부여한다")
    void findPopularRestaurants_Port반환순서_1부터연속된순위를부여한다() {
        // given
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        when(popularRestaurantQueryPort.findTopByFavoriteCount(20)).thenReturn(List.of(
                new PopularRestaurantRow(firstId, "첫째 맛집", "서울특별시 종로구 1", "한식", 30L),
                new PopularRestaurantRow(secondId, "둘째 맛집", "서울특별시 종로구 2", "한식", 20L),
                new PopularRestaurantRow(thirdId, "셋째 맛집", "서울특별시 종로구 3", "한식", 10L)));

        // when
        List<PopularRestaurantSummary> result = service.findPopularRestaurants();

        // then
        assertThat(result).extracting(PopularRestaurantSummary::rank).containsExactly(1, 2, 3);
        assertThat(result).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(firstId, secondId, thirdId);
        assertThat(result).extracting(PopularRestaurantSummary::favoriteCount)
                .containsExactly(30L, 20L, 10L);
    }

    @Test
    @DisplayName("조회 실패는 빈 결과로 흡수하지 않고 그대로 전파한다")
    void findPopularRestaurants_Port조회실패_예외를그대로전파한다() {
        // given
        when(popularRestaurantQueryPort.findTopByFavoriteCount(20))
                .thenThrow(new DataAccessResourceFailureException("집계 조회 실패"));

        // when
        // then
        assertThatThrownBy(service::findPopularRestaurants)
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("Port가 빈 목록을 반환하면 빈 결과를 반환한다")
    void findPopularRestaurants_Port가빈목록반환_빈결과를반환한다() {
        // given
        when(popularRestaurantQueryPort.findTopByFavoriteCount(20)).thenReturn(List.of());

        // when
        List<PopularRestaurantSummary> result = service.findPopularRestaurants();

        // then
        assertThat(result).isEmpty();
    }
}
