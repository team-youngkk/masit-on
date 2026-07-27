package com.masiton.restaurant.infrastructure.persistence;

import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * food_category 테이블과 매핑되는 JPA Entity다.
 * V1__create_reference_and_admin_tables.sql의 food_category 테이블 정의와 컬럼이 대응해야 한다.
 */
@Entity
@Table(name = "food_category")
public class FoodCategoryJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected FoodCategoryJpaEntity() {
    }

    public FoodCategoryJpaEntity(UUID id, String code, String name, short sortOrder, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = active;
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
}
