package com.masiton.visit.application.query;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;
import com.masiton.visit.application.port.in.FindValidVisitContentByRestaurantQuery;
import com.masiton.visit.application.port.in.RelatedVideoView;
import com.masiton.visit.application.port.in.VisitContentResult;
import com.masiton.visit.application.port.in.VisitedCreatorView;
import com.masiton.visit.application.port.out.VisitContentRow;
import com.masiton.visit.application.port.out.VisitQueryPort;

/**
 * transaction-boundaries.md 5절: 공개 조회 유스케이스는 읽기 전용 트랜잭션으로 Application
 * public 메서드에서 경계를 시작한다. VisitQueryPort가 이미 DB 조건으로 공개·유효 판정을
 * 적용하지만, 이 서비스가 Creator ID/Video ID 기준 최종 중복 제거와 안정 정렬을 보장한다.
 */
@Service
@Transactional(readOnly = true)
public class VisitQueryService
        implements FindDistinctValidRestaurantIdsByCreatorQuery, FindValidVisitContentByRestaurantQuery {

    private final VisitQueryPort visitQueryPort;

    public VisitQueryService(VisitQueryPort visitQueryPort) {
        this.visitQueryPort = visitQueryPort;
    }

    @Override
    public Set<UUID> findDistinctValidRestaurantIdsByCreator(UUID creatorId) {
        return Set.copyOf(visitQueryPort.findDistinctValidRestaurantIdsByCreatorId(creatorId));
    }

    @Override
    public VisitContentResult findValidVisitContentByRestaurant(UUID restaurantId) {
        List<VisitContentRow> rows = visitQueryPort.findValidVisitContentRowsByRestaurantId(restaurantId);

        Map<UUID, VisitedCreatorView> creatorsById = new LinkedHashMap<>();
        Map<UUID, RelatedVideoView> videosById = new LinkedHashMap<>();
        for (VisitContentRow row : rows) {
            creatorsById.putIfAbsent(
                    row.creatorId(),
                    new VisitedCreatorView(row.creatorId(), row.channelName(), row.channelUrl()));
            videosById.putIfAbsent(
                    row.videoId(),
                    new RelatedVideoView(
                            row.videoId(), row.title(), row.thumbnailUrl(), row.channelName(), row.sourceUrl()));
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
