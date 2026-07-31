package com.masiton.video.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;


/**
 * video 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다. creator는 다른 Aggregate를 직접 참조하지 않고
 * 식별자(UUID, nullable)로만 연관을 표현한다.
 */
public class Video {

    private final UUID id;
    private final UUID creatorId;
    private final String externalVideoId;
    private final String publisherExternalChannelId;
    private final String title;
    private final String sourceUrl;
    private final String thumbnailUrl;
    private final OffsetDateTime publishedAt;
    private final PublicationStatus publicationStatus;
    private final LifecycleStatus lifecycleStatus;
    private final ExternalAvailabilityStatus externalAvailabilityStatus;
    private final OffsetDateTime externalStatusCheckedAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime deletedAt;

    public Video(
            UUID id,
            UUID creatorId,
            String externalVideoId,
            String publisherExternalChannelId,
            String title,
            String sourceUrl,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            ExternalAvailabilityStatus externalAvailabilityStatus,
            OffsetDateTime externalStatusCheckedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt) {
        this.id = id;
        this.creatorId = creatorId;
        this.externalVideoId = externalVideoId;
        this.publisherExternalChannelId = publisherExternalChannelId;
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
        this.publicationStatus = publicationStatus;
        this.lifecycleStatus = lifecycleStatus;
        this.externalAvailabilityStatus = externalAvailabilityStatus;
        this.externalStatusCheckedAt = externalStatusCheckedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public String getExternalVideoId() {
        return externalVideoId;
    }

    public String getPublisherExternalChannelId() {
        return publisherExternalChannelId;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public LifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public ExternalAvailabilityStatus getExternalAvailabilityStatus() {
        return externalAvailabilityStatus;
    }

    public OffsetDateTime getExternalStatusCheckedAt() {
        return externalStatusCheckedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
