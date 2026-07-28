package com.masiton.orchestration.application.query;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.orchestration.application.port.in.FindValidVisitContentByRestaurantQuery;
import com.masiton.orchestration.application.port.in.RelatedVideoView;
import com.masiton.orchestration.application.port.in.VisitContentResult;
import com.masiton.orchestration.application.port.in.VisitedCreatorView;
import com.masiton.orchestration.application.port.out.RestaurantDetailContentQueryPort;
import com.masiton.orchestration.application.port.out.VisitContentRow;

/**
 * query-composition.md 5절: RestaurantDetailContentQueryPort가 DB에서 공개·유효 조건을 먼저
 * 적용한 Row를 반환하면, 이 Application이 Creator ID/Video ID 기준 최종 중복 제거와 안정 정렬을
 * 수행해 restaurant-detail-api.md 계약(visitedBy/videos)에 맞는 결과로 조합한다.
 * transaction-boundaries.md 5절에 따라 읽기 전용 트랜잭션은 이 public 메서드에서 시작한다.
 *
 * <p>Row의 Creator 필드는 항상 채워지지만 Video 필드는 그 Visit의 영상이 공개·유효 조건을
 * 만족하지 못하면 Port 구현에서 NULL로 온다(restaurant-detail-api.md 7절: 공개 영상이 없어도
 * 유효한 유튜버는 visitedBy에 표시). videoId가 NULL인 Row는 videos에 반영하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class VisitContentQueryService implements FindValidVisitContentByRestaurantQuery {

    private final RestaurantDetailContentQueryPort restaurantDetailContentQueryPort;

    public VisitContentQueryService(RestaurantDetailContentQueryPort restaurantDetailContentQueryPort) {
        this.restaurantDetailContentQueryPort = restaurantDetailContentQueryPort;
    }

    @Override
    public VisitContentResult findValidVisitContentByRestaurant(UUID restaurantId) {
        List<VisitContentRow> rows =
                restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId);

        Map<UUID, VisitedCreatorView> creatorsById = new LinkedHashMap<>();
        Map<UUID, RelatedVideoView> videosById = new LinkedHashMap<>();
        for (VisitContentRow row : rows) {
            creatorsById.putIfAbsent(
                    row.creatorId(),
                    new VisitedCreatorView(row.creatorId(), row.channelName(), row.channelUrl()));
            if (row.videoId() != null) {
                videosById.putIfAbsent(
                        row.videoId(),
                        new RelatedVideoView(
                                row.videoId(), row.title(), row.thumbnailUrl(), row.channelName(), row.sourceUrl()));
            }
        }

        List<VisitedCreatorView> visitedBy = creatorsById.values().stream()
                .sorted(Comparator.comparing(VisitedCreatorView::channelName)
                        .thenComparing(VisitedCreatorView::id))
                .toList();
        List<RelatedVideoView> videos = videosById.values().stream()
                .sorted(Comparator.comparing(RelatedVideoView::title)
                        .thenComparing(RelatedVideoView::id))
                .toList();

        return new VisitContentResult(visitedBy, videos);
    }
}
