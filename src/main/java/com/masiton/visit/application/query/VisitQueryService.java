package com.masiton.visit.application.query;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;
import com.masiton.visit.application.port.out.VisitQueryPort;

/**
 * transaction-boundaries.md 5절: 공개 조회 유스케이스는 읽기 전용 트랜잭션으로 Application
 * public 메서드에서 경계를 시작한다. VisitQueryPort가 이미 DB 조건으로 공개·유효 판정을
 * 적용하지만, 이 서비스가 Restaurant ID 기준 최종 중복 제거를 보장하고 Creator 자체의
 * 공개 가시성 여부를 별도로 조회해 결과에 함께 담는다.
 *
 * <p>맛집 상세 콘텐츠(방문 유튜버·관련 영상) 조회는 이 서비스의 책임이 아니다. 그 계약은
 * query-composition.md가 지정한 위치대로 {@code orchestration.application.query
 * .VisitContentQueryService}가 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class VisitQueryService implements FindDistinctValidRestaurantIdsByCreatorQuery {

    private final VisitQueryPort visitQueryPort;

    public VisitQueryService(VisitQueryPort visitQueryPort) {
        this.visitQueryPort = visitQueryPort;
    }

    @Override
    public CreatorRestaurantCandidates findDistinctValidRestaurantIdsByCreator(UUID creatorId) {
        boolean creatorPublic = visitQueryPort.isCreatorPubliclyVisible(creatorId);
        Set<UUID> restaurantIds = Set.copyOf(visitQueryPort.findDistinctValidRestaurantIdsByCreatorId(creatorId));
        return new CreatorRestaurantCandidates(creatorPublic, restaurantIds);
    }
}
