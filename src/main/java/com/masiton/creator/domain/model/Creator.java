package com.masiton.creator.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;


/**
 * creator 테이블에 대응하는 프레임워크 독립 도메인 모델이다.
 * 등록·상태 전환 같은 업무 규칙은 이 작업 범위가 아니며 이후 WS-04에서 추가된다.
 */
public class Creator {

    private final UUID id;
    private final String externalChannelId;
    private final String channelName;
    private final String channelUrl;
    private final PublicationStatus publicationStatus;
    private final LifecycleStatus lifecycleStatus;
    private final ExternalAvailabilityStatus externalAvailabilityStatus;
    private final OffsetDateTime externalStatusCheckedAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime deletedAt;

    public Creator(
            UUID id,
            String externalChannelId,
            String channelName,
            String channelUrl,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            ExternalAvailabilityStatus externalAvailabilityStatus,
            OffsetDateTime externalStatusCheckedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt) {
        this.id = id;
        this.externalChannelId = externalChannelId;
        this.channelName = channelName;
        this.channelUrl = channelUrl;
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
