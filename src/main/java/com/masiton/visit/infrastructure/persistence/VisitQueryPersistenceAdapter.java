package com.masiton.visit.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.visit.application.port.out.VisitContentRow;
import com.masiton.visit.application.port.out.VisitQueryPort;

/**
 * VisitQueryPort의 native SQL 기반 구현체다. Spring Data Projection과 도메인 간 결합을 막기 위해
 * VisitContentProjection을 application.port.out.VisitContentRow로 변환해 반환한다.
 */
@Component
class VisitQueryPersistenceAdapter implements VisitQueryPort {

    private final VisitQueryJpaRepository visitQueryJpaRepository;

    VisitQueryPersistenceAdapter(VisitQueryJpaRepository visitQueryJpaRepository) {
        this.visitQueryJpaRepository = visitQueryJpaRepository;
    }

    @Override
    public List<UUID> findDistinctValidRestaurantIdsByCreatorId(UUID creatorId) {
        return visitQueryJpaRepository.findDistinctValidRestaurantIdsByCreatorId(creatorId);
    }

    @Override
    public List<VisitContentRow> findValidVisitContentRowsByRestaurantId(UUID restaurantId) {
        return visitQueryJpaRepository.findValidVisitContentRowsByRestaurantId(restaurantId).stream()
                .map(row -> new VisitContentRow(
                        row.getCreatorId(),
                        row.getChannelName(),
                        row.getChannelUrl(),
                        row.getVideoId(),
                        row.getTitle(),
                        row.getThumbnailUrl(),
                        row.getSourceUrl()))
                .toList();
    }
}
