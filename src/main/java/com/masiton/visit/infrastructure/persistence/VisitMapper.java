package com.masiton.visit.infrastructure.persistence;

import com.masiton.visit.domain.model.Visit;

/**
 * VisitJpaEntity와 도메인 모델 Visit 사이의 변환만 담당한다.
 */
final class VisitMapper {

    private VisitMapper() {
    }

    static Visit toDomain(VisitJpaEntity entity) {
        return new Visit(
                entity.getId(),
                entity.getRestaurantId(),
                entity.getCreatorId(),
                entity.getVideoId(),
                entity.getPublicationStatus(),
                entity.getLifecycleStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static VisitJpaEntity toEntity(Visit visit) {
        return new VisitJpaEntity(
                visit.getId(),
                visit.getRestaurantId(),
                visit.getCreatorId(),
                visit.getVideoId(),
                visit.getPublicationStatus(),
                visit.getLifecycleStatus(),
                visit.getDeletedAt());
    }
}
