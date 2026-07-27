package com.masiton.restaurant.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * restaurant 테이블과 매핑되는 JPA Entity다.
 * V2__create_core_domain_tables.sql의 restaurant 테이블 정의와 컬럼이 대응해야 한다.
 * region_id, food_category_id는 dependency-rules.md 3절에 따라 객체 연관관계 대신
 * 식별자(UUID) 컬럼으로만 매핑한다.
 */
@Entity
@Table(name = "restaurant")
public class RestaurantJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "food_category_id", nullable = false)
    private UUID foodCategoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "kakao_place_id", nullable = false, length = 64)
    private String kakaoPlaceId;

    @Column(name = "kakao_place_url", nullable = false, length = 2048)
    private String kakaoPlaceUrl;

    @Column(name = "road_address", nullable = false, length = 255)
    private String roadAddress;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 16)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected RestaurantJpaEntity() {
    }

    public RestaurantJpaEntity(
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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
