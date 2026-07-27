package com.masiton.visit.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;
import com.masiton.visit.domain.model.LifecycleStatus;
import com.masiton.visit.domain.model.PublicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * visit 테이블과 매핑되는 JPA Entity다.
 * V2__create_core_domain_tables.sql의 visit 테이블 정의와 컬럼이 대응해야 한다.
 * restaurant_id·creator_id·video_id는 다른 도메인 Entity를 참조하지 않고 평범한 UUID 컬럼으로만 매핑한다.
 */
@Entity
@Table(name = "visit")
public class VisitJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Column(name = "video_id", nullable = false, updatable = false)
    private UUID videoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 16)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected VisitJpaEntity() {
    }

    public VisitJpaEntity(
            UUID id,
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            OffsetDateTime deletedAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.creatorId = creatorId;
        this.videoId = videoId;
        this.publicationStatus = publicationStatus;
        this.lifecycleStatus = lifecycleStatus;
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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
