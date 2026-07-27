package com.masiton.restaurant.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * region 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다.
 */
public class Region {

    private final UUID id;
    private final String code;
    private final String name;
    private final short sortOrder;
    private final boolean active;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public Region(
            UUID id,
            String code,
            String name,
            short sortOrder,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
