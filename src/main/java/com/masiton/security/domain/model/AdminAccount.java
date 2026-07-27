package com.masiton.security.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * admin_account 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다. 인증 로직(JWT 발급, 비밀번호 검증 등)은 이 타입의 책임이
 * 아니며 WS-04에서 구현하는 Application 계층이 담당한다.
 */
public class AdminAccount {

    private final UUID id;
    private final String loginId;
    private final String passwordHash;
    private final AdminRole role;
    private final boolean active;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public AdminAccount(
            UUID id,
            String loginId,
            String passwordHash,
            AdminRole role,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
