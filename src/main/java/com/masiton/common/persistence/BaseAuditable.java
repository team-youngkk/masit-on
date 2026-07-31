package com.masiton.common.persistence;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * restaurant, creator, video, visit 네 테이블이 공유하는 감사 컬럼(createdAt, updatedAt)이다.
 * deletedAt은 {@code ck_{table}__deleted_pair} CHECK와 얽혀 도메인마다 전환 시점이 달라
 * 각 Entity가 직접 선언한다.
 *
 * <p>Lombok은 build.gradle에 선언돼 있지 않아(도입 시 별도 승인 필요) 사용하지 않고
 * 공개 setter 없이 getter만 수동으로 제공한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected BaseAuditable() {
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
