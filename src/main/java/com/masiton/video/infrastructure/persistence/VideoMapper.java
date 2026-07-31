package com.masiton.video.infrastructure.persistence;

import com.masiton.video.domain.model.Video;

/**
 * VideoJpaEntity와 도메인 모델 Video 사이의 변환만 담당한다.
 */
final class VideoMapper {

    private VideoMapper() {
    }

    static Video toDomain(VideoJpaEntity entity) {
        return new Video(
                entity.getId(),
                entity.getCreatorId(),
                entity.getExternalVideoId(),
                entity.getPublisherExternalChannelId(),
                entity.getTitle(),
                entity.getSourceUrl(),
                entity.getThumbnailUrl(),
                entity.getPublishedAt(),
                entity.getPublicationStatus(),
                entity.getLifecycleStatus(),
                entity.getExternalAvailabilityStatus(),
                entity.getExternalStatusCheckedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static VideoJpaEntity toEntity(Video video) {
        return new VideoJpaEntity(
                video.getId(),
                video.getCreatorId(),
                video.getExternalVideoId(),
                video.getPublisherExternalChannelId(),
                video.getTitle(),
                video.getSourceUrl(),
                video.getThumbnailUrl(),
                video.getPublishedAt(),
                video.getPublicationStatus(),
                video.getLifecycleStatus(),
                video.getExternalAvailabilityStatus(),
                video.getExternalStatusCheckedAt(),
                video.getDeletedAt());
    }
}
