package com.masiton.video.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;
import com.masiton.common.persistence.LifecycleStatus;
import com.masiton.common.persistence.PublicationStatus;
import com.masiton.video.domain.model.ExternalAvailabilityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * video 테이블과 매핑되는 JPA Entity다.
 * creator_id는 다른 도메인(Creator)의 Entity를 참조하지 않고
 * 평범한 컬럼(UUID, nullable)으로만 매핑한다.
 */
@Entity
@Table(name = "video")
public class VideoJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "external_video_id", nullable = false, length = 32)
    private String externalVideoId;

    @Column(name = "publisher_external_channel_id", nullable = false, length = 64)
    private String publisherExternalChannelId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "thumbnail_url", nullable = false, length = 2048)
    private String thumbnailUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 16)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_availability_status", nullable = false, length = 16)
    private ExternalAvailabilityStatus externalAvailabilityStatus;

    @Column(name = "external_status_checked_at", nullable = false)
    private OffsetDateTime externalStatusCheckedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected VideoJpaEntity() {
    }

    public VideoJpaEntity(
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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
