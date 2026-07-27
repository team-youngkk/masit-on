package com.masiton.security.infrastructure.persistence;

import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;
import com.masiton.security.domain.model.AdminRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * admin_account 테이블과 매핑되는 JPA Entity다.
 * V1__create_reference_and_admin_tables.sql의 admin_account 테이블 정의와 컬럼이 대응해야 한다.
 */
@Entity
@Table(name = "admin_account")
public class AdminAccountJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "login_id", nullable = false, length = 100)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AdminRole role;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AdminAccountJpaEntity() {
    }

    public AdminAccountJpaEntity(
            UUID id,
            String loginId,
            String passwordHash,
            AdminRole role,
            boolean active) {
        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AdminRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
