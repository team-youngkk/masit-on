package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.port.in.FindValidVisitContentByRestaurantQuery;
import com.masiton.orchestration.application.port.in.RelatedVideoView;
import com.masiton.orchestration.application.port.in.VisitContentResult;
import com.masiton.orchestration.application.port.in.VisitedCreatorView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * query-composition.md 4절의 조회 순서·부분 실패 규칙을 Application 단위로 검증한다.
 * 콘텐츠 중복 제거·정렬은 {@link FindValidVisitContentByRestaurantQuery} 구현체
 * ({@code VisitContentQueryService}, WS-03 소유)가 이미 수행하므로 이 테스트는
 * 그 결과를 재조합 없이 그대로 전달하는지만 검증한다.
 */
@DisplayName("맛집 상세 조회 조합")
class RestaurantDetailQueryServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    private final RestaurantDetailBaseQueryPort baseQueryPort = mock(RestaurantDetailBaseQueryPort.class);
    private final FindValidVisitContentByRestaurantQuery findValidVisitContentByRestaurantQuery =
            mock(FindValidVisitContentByRestaurantQuery.class);
    private final RestaurantDetailQueryService service =
            new RestaurantDetailQueryService(baseQueryPort, findValidVisitContentByRestaurantQuery);

    @Test
    @DisplayName("기본 정보 Port가 빈 결과를 반환하면 RESTAURANT_NOT_FOUND 코드의 예외를 던진다")
    void 상세조회_기본정보없음_RESTAURANT_NOT_FOUND예외를던진다() {
        // given
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getRestaurantDetail(RESTAURANT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("RESTAURANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("콘텐츠 Query가 예외를 던지면 기본 정보는 유지하고 TEMPORARILY_UNAVAILABLE과 빈 목록을 반환한다")
    void 상세조회_콘텐츠조회실패_기본정보유지하고TEMPORARILY_UNAVAILABLE을반환한다() {
        // given
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(findValidVisitContentByRestaurantQuery.findValidVisitContentByRestaurant(RESTAURANT_ID))
                .thenThrow(new RuntimeException("일시적인 저장소 오류"));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.TEMPORARILY_UNAVAILABLE);
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
        assertThat(result.name()).isEqualTo("테스트 맛집");
        assertThat(result.kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/example");
    }

    @Test
    @DisplayName("콘텐츠 Query가 빈 결과를 반환하면 AVAILABLE과 빈 목록을 반환한다")
    void 상세조회_콘텐츠없음_AVAILABLE과빈목록을반환한다() {
        // given
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(findValidVisitContentByRestaurantQuery.findValidVisitContentByRestaurant(RESTAURANT_ID))
                .thenReturn(new VisitContentResult(List.of(), List.of()));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.AVAILABLE);
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 Query가 반환한 결과를 재조합 없이 그대로 응답에 담는다")
    void 상세조회_콘텐츠존재_Query결과를그대로전달한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        VisitedCreatorView creatorView = new VisitedCreatorView(creatorId, "채널A", "https://youtube.com/channelA");
        RelatedVideoView videoView =
                new RelatedVideoView(videoId, "영상1", "https://thumb/1", "채널A", "https://source/1");
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(findValidVisitContentByRestaurantQuery.findValidVisitContentByRestaurant(RESTAURANT_ID))
                .thenReturn(new VisitContentResult(List.of(creatorView), List.of(videoView)));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.AVAILABLE);
        assertThat(result.visitedBy()).containsExactly(creatorView);
        assertThat(result.videos()).containsExactly(videoView);
    }

    private RestaurantDetailBase baseFixture() {
        return new RestaurantDetailBase(
                RESTAURANT_ID,
                "테스트 맛집",
                "한식",
                "서울특별시 종로구 종로 1",
                "2층",
                "02-000-0000",
                "https://place.map.kakao.com/example"
        );
    }
}
