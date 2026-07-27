package com.masiton.creator.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;
import com.masiton.common.persistence.LifecycleStatus;
import com.masiton.common.persistence.PublicationStatus;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * creator 테이블 JPA 매핑이다. PK는 애플리케이션이 저장 전에 생성한 UUID v4를 사용하므로
 * {@code @GeneratedValue}를 두지 않는다(ADR-DATA-007).
 */
@Entity
@Table(name = "creator")
public class CreatorJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_channel_id", nullable = false, length = 64)
    private String externalChannelId;

    @Column(name = "channel_name", nullable = false, columnDefinition = "text")
    private String channelName;

    @Column(name = "channel_url", nullable = false, length = 2048)
    private String channelUrl;

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

    protected CreatorJpaEntity() {
    }

    public CreatorJpaEntity(
            UUID id,
            String externalChannelId,
            String channelName,
            String channelUrl,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            ExternalAvailabilityStatus externalAvailabilityStatus,
            OffsetDateTime externalStatusCheckedAt,
            OffsetDateTime deletedAt) {
        this.id = id;
        this.externalChannelId = externalChannelId;
        this.channelName = channelName;
        this.channelUrl = channelUrl;
        this.publicationStatus = publicationStatus;
        this.lifecycleStatus = lifecycleStatus;
        this.externalAvailabilityStatus = externalAvailabilityStatus;
        this.externalStatusCheckedAt = externalStatusCheckedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalChannelId() {
        return externalChannelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getChannelUrl() {
        return channelUrl;
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
