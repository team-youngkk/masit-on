package com.masiton.visit.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.visit.application.port.out.VisitQueryPort;

/**
 * VisitQueryPort의 native SQL 기반 구현체다.
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
    public boolean isCreatorPubliclyVisible(UUID creatorId) {
        return visitQueryJpaRepository.existsPublicCreator(creatorId);
    }
}
