package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.orchestration.application.port.in.VisitContentResult;
import com.masiton.orchestration.application.port.out.RestaurantDetailContentQueryPort;
import com.masiton.orchestration.application.port.out.VisitContentRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RestaurantDetailContentQueryPort를 Mockito로 대역해 VisitContentQueryService의 중복 제거·정렬
 * 위임 로직만 검증한다. 실제 공개·유효 판정 SQL 조건은 VisitContentQueryIntegrationTest가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VisitContentQueryService")
class VisitContentQueryServiceTest {

    @Mock
    private RestaurantDetailContentQueryPort restaurantDetailContentQueryPort;

    @Test
    @DisplayName("Restaurant기준_콘텐츠조회_같은Creator의서로다른영상Row를Creator기준한번만반환하고채널명순으로정렬한다")
    void Restaurant기준_콘텐츠조회_같은Creator의서로다른영상Row를Creator기준한번만반환하고채널명순으로정렬한다() {
        // given
        VisitContentQueryService service = new VisitContentQueryService(restaurantDetailContentQueryPort);
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId1 = UUID.randomUUID();
        UUID videoId2 = UUID.randomUUID();
        UUID otherCreatorId = UUID.randomUUID();
        UUID otherVideoId = UUID.randomUUID();

        List<VisitContentRow> rows = List.of(
                new VisitContentRow(
                        creatorId, "나채널", "https://youtube.com/a", videoId1, "b영상", "thumb1", "source1"),
                new VisitContentRow(
                        creatorId, "나채널", "https://youtube.com/a", videoId2, "a영상", "thumb2", "source2"),
                new VisitContentRow(
                        otherCreatorId, "가채널", "https://youtube.com/b", otherVideoId, "c영상", "thumb3", "source3"));
        when(restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId))
                .thenReturn(rows);

        // when
        VisitContentResult result = service.findValidVisitContentByRestaurant(restaurantId);

        // then: visitedBy는 Creator ID 기준 중복 제거 후 channelName 오름차순
        assertThat(result.visitedBy()).hasSize(2);
        assertThat(result.visitedBy().get(0).id()).isEqualTo(otherCreatorId);
        assertThat(result.visitedBy().get(0).channelUrl()).isEqualTo("https://youtube.com/b");
        assertThat(result.visitedBy().get(1).id()).isEqualTo(creatorId);
        assertThat(result.visitedBy().get(1).channelUrl()).isEqualTo("https://youtube.com/a");

        // then: videos는 중복 제거 없이 title 오름차순(같은 Creator라도 Video는 서로 다름)
        assertThat(result.videos()).hasSize(3);
        assertThat(result.videos().get(0).title()).isEqualTo("a영상");
        assertThat(result.videos().get(0).thumbnailUrl()).isEqualTo("thumb2");
        assertThat(result.videos().get(0).sourceUrl()).isEqualTo("source2");
        assertThat(result.videos().get(1).title()).isEqualTo("b영상");
        assertThat(result.videos().get(1).thumbnailUrl()).isEqualTo("thumb1");
        assertThat(result.videos().get(1).sourceUrl()).isEqualTo("source1");
        assertThat(result.videos().get(2).title()).isEqualTo("c영상");
        assertThat(result.videos().get(2).thumbnailUrl()).isEqualTo("thumb3");
        assertThat(result.videos().get(2).sourceUrl()).isEqualTo("source3");
    }

    @Test
    @DisplayName("Restaurant기준_콘텐츠조회_같은Video가Row로두번오면VideoID기준한번만반환한다")
    void Restaurant기준_콘텐츠조회_같은Video가Row로두번오면VideoID기준한번만반환한다() {
        // given
        VisitContentQueryService service = new VisitContentQueryService(restaurantDetailContentQueryPort);
        UUID restaurantId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        List<VisitContentRow> rows = List.of(
                new VisitContentRow(
                        creatorId, "채널", "https://youtube.com/a", videoId, "제목", "thumb", "source"),
                new VisitContentRow(
                        creatorId, "채널", "https://youtube.com/a", videoId, "제목", "thumb", "source"));
        when(restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId))
                .thenReturn(rows);

        // when
        VisitContentResult result = service.findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.videos()).hasSize(1);
        assertThat(result.visitedBy()).hasSize(1);
    }

    @Test
    @DisplayName("Restaurant기준_콘텐츠조회_채널명과영상제목이같으면ID오름차순으로정렬한다")
    void Restaurant기준_콘텐츠조회_채널명과영상제목이같으면ID오름차순으로정렬한다() {
        // given
        VisitContentQueryService service = new VisitContentQueryService(restaurantDetailContentQueryPort);
        UUID restaurantId = UUID.randomUUID();
        UUID firstCreatorId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID secondCreatorId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID firstVideoId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID secondVideoId = UUID.fromString("10000000-0000-4000-8000-000000000002");

        List<VisitContentRow> rows = List.of(
                new VisitContentRow(
                        secondCreatorId, "같은채널", "https://youtube.com/b",
                        secondVideoId, "같은제목", "thumb2", "source2"),
                new VisitContentRow(
                        firstCreatorId, "같은채널", "https://youtube.com/a",
                        firstVideoId, "같은제목", "thumb1", "source1"));
        when(restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId))
                .thenReturn(rows);

        // when
        VisitContentResult result = service.findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).extracting("id")
                .containsExactly(firstCreatorId, secondCreatorId);
        assertThat(result.videos()).extracting("id")
                .containsExactly(firstVideoId, secondVideoId);
    }

    @Test
    @DisplayName("Restaurant기준_콘텐츠조회_Row의videoId가null이면VisitRow전체를제외한다")
    void Restaurant기준_콘텐츠조회_Row의videoId가null이면VisitRow전체를제외한다() {
        // given: 운영 Port는 이 Row를 만들지 않지만 Application도 유효한 영상 없는 관계를 노출하지 않는다.
        VisitContentQueryService service = new VisitContentQueryService(restaurantDetailContentQueryPort);
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        List<VisitContentRow> rows = List.of(
                new VisitContentRow(creatorId, "채널", "https://youtube.com/a", null, null, null, null));
        when(restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId))
                .thenReturn(rows);

        // when
        VisitContentResult result = service.findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("Restaurant기준_콘텐츠조회_관계없으면두목록모두빈배열을반환한다")
    void Restaurant기준_콘텐츠조회_관계없으면두목록모두빈배열을반환한다() {
        // given
        VisitContentQueryService service = new VisitContentQueryService(restaurantDetailContentQueryPort);
        UUID restaurantId = UUID.randomUUID();
        when(restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId))
                .thenReturn(List.of());

        // when
        VisitContentResult result = service.findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }
}
