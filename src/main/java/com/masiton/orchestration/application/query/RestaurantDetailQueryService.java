package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.port.in.FindValidVisitContentByRestaurantQuery;
import com.masiton.orchestration.application.port.in.GetRestaurantDetailQuery;
import com.masiton.orchestration.application.port.in.RelatedVideoView;
import com.masiton.orchestration.application.port.in.VisitContentResult;
import com.masiton.orchestration.application.port.in.VisitedCreatorView;

/**
 * 맛집 상세 응답을 조합하는 전용 Application Query 책임이다. Restaurant 소유 도메인이 아니며
 * {@link RestaurantDetailBaseQueryPort}(기본 정보)와 {@link FindValidVisitContentByRestaurantQuery}
 * (WS-03이 소유한 방문 콘텐츠 조회 계약)만 호출한다. 콘텐츠 중복 제거·정렬은
 * {@code VisitContentQueryService}가 이미 restaurant-detail-api.md 계약대로 수행하므로
 * 이 클래스는 재조합하지 않고 그대로 전달한다.
 *
 * <p>query-composition.md 4절의 조회 순서와 부분 실패 규칙을 그대로 구현한다.
 * 콘텐츠 Port 실패는 기본 정보 조회 실패와 분리해 전체 요청을 실패시키지 않고
 * {@link ContentStatus#TEMPORARILY_UNAVAILABLE}로 격리한다.
 *
 * <p>transaction-boundaries.md 2·5절: 공개 상세 Query의 트랜잭션 경계는 이 Query Service의
 * public 메서드이며 읽기 전용이다. 콘텐츠 조회({@code VisitContentQueryService})는 자신의
 * {@code @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)}로 독립된
 * 물리 트랜잭션을 강제하므로, 이 메서드가 참여(REQUIRED)하는 바깥 트랜잭션과 별개다. 콘텐츠
 * 호출이 예외를 던져도 그 실패는 콘텐츠 쪽 트랜잭션 안에서 끝나고, 이 메서드가 예외를 catch해
 * 정상 반환하면 바깥 트랜잭션은 rollback-only로 표시되지 않아 정상 커밋된다
 * ({@code UnexpectedRollbackException} 없음).
 */
@Service
@Transactional(readOnly = true)
public class RestaurantDetailQueryService implements GetRestaurantDetailQuery {

    private static final Logger log = LoggerFactory.getLogger(RestaurantDetailQueryService.class);

    private final RestaurantDetailBaseQueryPort restaurantDetailBaseQueryPort;
    private final FindValidVisitContentByRestaurantQuery findValidVisitContentByRestaurantQuery;

    public RestaurantDetailQueryService(
            RestaurantDetailBaseQueryPort restaurantDetailBaseQueryPort,
            FindValidVisitContentByRestaurantQuery findValidVisitContentByRestaurantQuery
    ) {
        this.restaurantDetailBaseQueryPort = restaurantDetailBaseQueryPort;
        this.findValidVisitContentByRestaurantQuery = findValidVisitContentByRestaurantQuery;
    }

    @Override
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
        VisitContentResult content;
        try {
            content = findValidVisitContentByRestaurantQuery.findValidVisitContentByRestaurant(restaurantId);
        } catch (Exception exception) {
            log.warn("맛집 상세 콘텐츠 조회 실패: restaurantId={}", restaurantId, exception);
            return new ContentOutcome(ContentStatus.TEMPORARILY_UNAVAILABLE, List.of(), List.of());
        }
        return new ContentOutcome(ContentStatus.AVAILABLE, content.visitedBy(), content.videos());
    }

    private record ContentOutcome(
            ContentStatus contentStatus,
            List<VisitedCreatorView> visitedBy,
            List<RelatedVideoView> videos
    ) {
    }
}
