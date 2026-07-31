package com.masiton.orchestration.application.query;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * <p>Port는 BR-VISIT-005에 따라 Visit·Restaurant·Creator·Video가 모두 공개·유효한
 * Row만 반환한다. 이 서비스도 방어적으로 {@code videoId == null}인 Row 전체를
 * 제외해 유효한 근거 영상 없이 Creator만 노출되지 않게 한다.
 *
 * <p>{@code RestaurantDetailQueryService}가 기본 정보 조회와 함께 이 메서드를 호출할 때도 항상
 * 읽기 전용 트랜잭션 경계는 지켜야 한다(transaction-boundaries.md 2·5절). 그래서 propagation을
 * {@link Propagation#REQUIRES_NEW}로 둔다: 이 호출이 바깥의 상세 조회 트랜잭션에 참여(REQUIRED)하면
 * 이 메서드가 던진 예외로 바깥 트랜잭션이 rollback-only로 표시돼, 호출자가 예외를 catch해 정상
 * 반환해도 바깥 트랜잭션 커밋 시점에 {@code UnexpectedRollbackException}이 발생한다.
 * REQUIRES_NEW로 독립된 물리 트랜잭션을 강제하면 이 메서드의 실패가 자신만의 트랜잭션 안에서
 * 끝나고, 바깥 상세 조회 트랜잭션은 영향받지 않는다.
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
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
            if (row.videoId() == null) {
                continue;
            }
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
