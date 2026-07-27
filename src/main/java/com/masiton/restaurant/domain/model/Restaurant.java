package com.masiton.restaurant.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.common.persistence.LifecycleStatus;
import com.masiton.common.persistence.PublicationStatus;

/**
 * restaurant 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다. region·food_category는 다른 Aggregate를 직접 참조하지 않고
 * 식별자(UUID)로만 연관을 표현한다.
 */
public class Restaurant {

    private final UUID id;
    private final UUID regionId;
    private final UUID foodCategoryId;
    private final String name;
    private final String kakaoPlaceId;
    private final String kakaoPlaceUrl;
    private final String roadAddress;
    private final String detailAddress;
    private final String phoneNumber;
    private final PublicationStatus publicationStatus;
    private final LifecycleStatus lifecycleStatus;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime deletedAt;

    public Restaurant(
            UUID id,
            UUID regionId,
            UUID foodCategoryId,
            String name,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            PublicationStatus publicationStatus,
            LifecycleStatus lifecycleStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt) {
        this.id = id;
        this.regionId = regionId;
        this.foodCategoryId = foodCategoryId;
        this.name = name;
        this.kakaoPlaceId = kakaoPlaceId;
        this.kakaoPlaceUrl = kakaoPlaceUrl;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
        this.phoneNumber = phoneNumber;
        this.publicationStatus = publicationStatus;
        this.lifecycleStatus = lifecycleStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRegionId() {
        return regionId;
    }

    public UUID getFoodCategoryId() {
        return foodCategoryId;
    }

    public String getName() {
        return name;
    }

    public String getKakaoPlaceId() {
        return kakaoPlaceId;
    }

    public String getKakaoPlaceUrl() {
        return kakaoPlaceUrl;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    public String getPhoneNumber() {
        return phoneNumber;
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
