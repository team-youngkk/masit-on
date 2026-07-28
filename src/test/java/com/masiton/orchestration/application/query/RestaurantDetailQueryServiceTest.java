package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * query-composition.md 4·11절의 조회 순서·부분 실패·정렬 규칙을 Application 단위로 검증한다.
 * 실제 저장소 대신 두 Port를 Mock으로 대체한다.
 */
@DisplayName("맛집 상세 조회 조합")
class RestaurantDetailQueryServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    private final RestaurantDetailBaseQueryPort baseQueryPort = mock(RestaurantDetailBaseQueryPort.class);
    private final RestaurantDetailContentQueryPort contentQueryPort = mock(RestaurantDetailContentQueryPort.class);
    private final RestaurantDetailQueryService service =
            new RestaurantDetailQueryService(baseQueryPort, contentQueryPort);

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
    @DisplayName("콘텐츠 Port가 예외를 던지면 기본 정보는 유지하고 TEMPORARILY_UNAVAILABLE과 빈 목록을 반환한다")
    void 상세조회_콘텐츠조회실패_기본정보유지하고TEMPORARILY_UNAVAILABLE을반환한다() {
        // given
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID))
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
    @DisplayName("콘텐츠 Port가 빈 목록을 반환하면 AVAILABLE과 빈 목록을 반환한다")
    void 상세조회_콘텐츠없음_AVAILABLE과빈목록을반환한다() {
        // given
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID)).thenReturn(List.of());

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.AVAILABLE);
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("같은 유튜버가 서로 다른 두 영상에 방문했으면 유튜버는 한 명, 영상은 두 건으로 중복 제거한다")
    void 상세조회_같은유튜버서로다른영상두건_유튜버한명영상두건으로중복제거한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(
                new VisitContentRow(
                        creatorId, "채널A", "https://youtube.com/channelA",
                        UUID.randomUUID(), "영상1", "https://thumb/1", "https://source/1"),
                new VisitContentRow(
                        creatorId, "채널A", "https://youtube.com/channelA",
                        UUID.randomUUID(), "영상2", "https://thumb/2", "https://source/2")
        ));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.AVAILABLE);
        assertThat(result.visitedBy()).hasSize(1);
        assertThat(result.visitedBy().get(0).id()).isEqualTo(creatorId);
        assertThat(result.videos()).hasSize(2);
    }

    @Test
    @DisplayName("visitedBy는 channelName 오름차순, 같은 이름이면 id 오름차순으로 정렬한다")
    void 상세조회_같은채널명서로다른id_channelName오름차순동일하면id오름차순으로정렬한다() {
        // given
        UUID smallerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID largerId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(
                new VisitContentRow(
                        largerId, "같은채널", "https://youtube.com/b",
                        UUID.randomUUID(), "Zeta 영상", "https://thumb/b", "https://source/b"),
                new VisitContentRow(
                        smallerId, "같은채널", "https://youtube.com/a",
                        UUID.randomUUID(), "Alpha 영상", "https://thumb/a", "https://source/a")
        ));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.visitedBy())
                .extracting(VisitedCreatorView::id)
                .containsExactly(smallerId, largerId);
    }

    @Test
    @DisplayName("videos는 title 오름차순, 같은 제목이면 id 오름차순으로 정렬한다")
    void 상세조회_같은제목서로다른id_title오름차순동일하면id오름차순으로정렬한다() {
        // given
        UUID smallerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID largerId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(
                new VisitContentRow(
                        UUID.randomUUID(), "채널A", "https://youtube.com/a",
                        largerId, "같은제목", "https://thumb/b", "https://source/b"),
                new VisitContentRow(
                        UUID.randomUUID(), "채널B", "https://youtube.com/b",
                        smallerId, "같은제목", "https://thumb/a", "https://source/a")
        ));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.videos())
                .extracting(RelatedVideoView::id)
                .containsExactly(smallerId, largerId);
    }

    @Test
    @DisplayName(
            "id 정렬은 UUID의 부호 있는 long 비교가 아닌 문자열/DB 오름차순을 따른다: "
                    + "0x7f로 시작하는 id가 0x80으로 시작하는 id보다 앞선다")
    void 상세조회_id_signed부호경계UUID_문자열오름차순으로visitedBy를정렬한다() {
        // given: mostSigBits 최상위 비트가 갈리는 두 UUID다.
        // UUID.compareTo()는 mostSigBits를 부호 있는 long으로 비교하므로 0x80...으로 시작하는 값을
        // 음수로 취급해 앞에 놓지만, 문자열/DB 오름차순은 0x7f...가 먼저다.
        UUID stringOrderSmaller = UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff");
        UUID stringOrderLarger = UUID.fromString("80000000-0000-4000-8000-000000000000");
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(
                new VisitContentRow(
                        stringOrderLarger, "같은채널", "https://youtube.com/larger",
                        UUID.randomUUID(), "영상A", "https://thumb/a", "https://source/a"),
                new VisitContentRow(
                        stringOrderSmaller, "같은채널", "https://youtube.com/smaller",
                        UUID.randomUUID(), "영상B", "https://thumb/b", "https://source/b")
        ));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.visitedBy())
                .extracting(VisitedCreatorView::id)
                .containsExactly(stringOrderSmaller, stringOrderLarger);
    }

    @Test
    @DisplayName(
            "videos의 id 정렬도 UUID의 부호 있는 long 비교가 아닌 문자열/DB 오름차순을 따른다: "
                    + "0x7f로 시작하는 id가 0x80으로 시작하는 id보다 앞선다")
    void 상세조회_videos_id_signed부호경계UUID_문자열오름차순으로정렬한다() {
        // given
        UUID stringOrderSmaller = UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff");
        UUID stringOrderLarger = UUID.fromString("80000000-0000-4000-8000-000000000000");
        when(baseQueryPort.findPublicDetailById(RESTAURANT_ID)).thenReturn(Optional.of(baseFixture()));
        when(contentQueryPort.findPublicContentByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(
                new VisitContentRow(
                        UUID.randomUUID(), "채널A", "https://youtube.com/a",
                        stringOrderLarger, "같은제목", "https://thumb/a", "https://source/a"),
                new VisitContentRow(
                        UUID.randomUUID(), "채널B", "https://youtube.com/b",
                        stringOrderSmaller, "같은제목", "https://thumb/b", "https://source/b")
        ));

        // when
        RestaurantDetailResult result = service.getRestaurantDetail(RESTAURANT_ID);

        // then
        assertThat(result.videos())
                .extracting(RelatedVideoView::id)
                .containsExactly(stringOrderSmaller, stringOrderLarger);
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
