package com.masiton.visit.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;


/**
 * visit 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다. restaurant·creator·video는 다른 Aggregate를 직접 참조하지 않고
 * 식별자(UUID)로만 연관을 표현한다.
 */
public class Visit {

    private final UUID id;
    private final UUID restaurantId;
    private final UUID creatorId;
    private final UUID videoId;
    private final PublicationStatus publicationStatus;
    private final LifecycleStatus lifecycleStatus;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime deletedAt;

    public static Visit register(UUID id, UUID restaurantId, UUID creatorId, UUID videoId, boolean evidenceConfirmed) {
        if (!evidenceConfirmed) {
            throw new VisitEvidenceRequiredException();
        }
        return new Visit(
                id,
                restaurantId,
                creatorId,
                videoId,
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                null,
                null,
                null);
    }

    public Visit(
            UUID id,
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.creatorId = creatorId;
        this.videoId = videoId;
        this.publicationStatus = publicationStatus;
        this.lifecycleStatus = lifecycleStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public UUID getVideoId() {
        return videoId;
    }

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public LifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
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
