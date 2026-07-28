package com.masiton.orchestration.application.query;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;

/**
 * 맛집 상세 응답을 조합하는 전용 Application Query 책임이다. Restaurant 소유 도메인이 아니며
 * {@link RestaurantDetailBaseQueryPort}, {@link RestaurantDetailContentQueryPort} 두 출력 Port만 호출한다.
 *
 * <p>query-composition.md 4절의 조회 순서와 부분 실패 규칙을 그대로 구현한다.
 * 콘텐츠 Port 실패는 기본 정보 조회 실패와 분리해 전체 요청을 실패시키지 않고
 * {@link ContentStatus#TEMPORARILY_UNAVAILABLE}로 격리한다.
 */
@Service
public class RestaurantDetailQueryService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantDetailQueryService.class);

    private final RestaurantDetailBaseQueryPort restaurantDetailBaseQueryPort;
    private final RestaurantDetailContentQueryPort restaurantDetailContentQueryPort;

    public RestaurantDetailQueryService(
            RestaurantDetailBaseQueryPort restaurantDetailBaseQueryPort,
            RestaurantDetailContentQueryPort restaurantDetailContentQueryPort
    ) {
        this.restaurantDetailBaseQueryPort = restaurantDetailBaseQueryPort;
        this.restaurantDetailContentQueryPort = restaurantDetailContentQueryPort;
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResult getRestaurantDetail(UUID restaurantId) {
        RestaurantDetailBase base = restaurantDetailBaseQueryPort.findPublicDetailById(restaurantId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "요청한 맛집을 찾을 수 없습니다."));

        ContentOutcome outcome = fetchContent(restaurantId);

        return new RestaurantDetailResult(
                base.id(),
                base.name(),
                base.categoryName(),
                base.roadAddress(),
                base.detailAddress(),
                base.phoneNumber(),
                base.kakaoPlaceUrl(),
                outcome.contentStatus,
                outcome.visitedBy,
                outcome.videos
        );
    }

    private ContentOutcome fetchContent(UUID restaurantId) {
        List<VisitContentRow> rows;
        try {
            rows = restaurantDetailContentQueryPort.findPublicContentByRestaurantId(restaurantId);
        } catch (Exception exception) {
            log.warn("맛집 상세 콘텐츠 조회 실패: restaurantId={}", restaurantId, exception);
            return new ContentOutcome(ContentStatus.TEMPORARILY_UNAVAILABLE, List.of(), List.of());
        }
        return new ContentOutcome(ContentStatus.AVAILABLE, distinctCreators(rows), distinctVideos(rows));
    }

    private List<VisitedCreatorView> distinctCreators(List<VisitContentRow> rows) {
        return dedupe(
                rows,
                VisitContentRow::creatorId,
                row -> new VisitedCreatorView(row.creatorId(), row.channelName(), row.channelUrl()),
                Comparator.comparing(VisitedCreatorView::channelName)
                        .thenComparing(view -> view.id().toString())
        );
    }

    private List<RelatedVideoView> distinctVideos(List<VisitContentRow> rows) {
        return dedupe(
                rows,
                VisitContentRow::videoId,
                row -> new RelatedVideoView(
                        row.videoId(), row.title(), row.thumbnailUrl(), row.channelName(), row.sourceUrl()),
                Comparator.comparing(RelatedVideoView::title)
                        .thenComparing(view -> view.id().toString())
        );
    }

    private <K, V> List<V> dedupe(
            List<VisitContentRow> rows,
            Function<VisitContentRow, K> keyExtractor,
            Function<VisitContentRow, V> mapper,
            Comparator<V> comparator
    ) {
        return rows.stream()
                .collect(Collectors.toMap(keyExtractor, mapper, (first, second) -> first))
                .values()
                .stream()
                .sorted(comparator)
                .toList();
    }

    private record ContentOutcome(
            ContentStatus contentStatus,
            List<VisitedCreatorView> visitedBy,
            List<RelatedVideoView> videos
    ) {
    }
}
