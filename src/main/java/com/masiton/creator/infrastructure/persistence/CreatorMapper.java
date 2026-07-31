package com.masiton.creator.infrastructure.persistence;

import com.masiton.creator.domain.model.Creator;

/**
 * JPA Entity와 domain.model 간 변환만 담당한다. 업무 규칙을 포함하지 않는다.
 */
final class CreatorMapper {

    private CreatorMapper() {
    }

    public static Creator toDomain(CreatorJpaEntity entity) {
        return new Creator(
                entity.getId(),
                entity.getExternalChannelId(),
                entity.getChannelName(),
                entity.getChannelUrl(),
                entity.getProfileImageUrl(),
                entity.getDescription(),
                entity.getHandle(),
                entity.getPublicationStatus(),
                entity.getLifecycleStatus(),
                entity.getExternalAvailabilityStatus(),
                entity.getExternalStatusCheckedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    public static CreatorJpaEntity toEntity(Creator domain) {
        return new CreatorJpaEntity(
                domain.getId(),
                domain.getExternalChannelId(),
                domain.getChannelName(),
                domain.getChannelUrl(),
                domain.getProfileImageUrl(),
                domain.getDescription(),
                domain.getHandle(),
                domain.getPublicationStatus(),
                domain.getLifecycleStatus(),
                domain.getExternalAvailabilityStatus(),
                domain.getExternalStatusCheckedAt(),
                domain.getDeletedAt());
    }
}
