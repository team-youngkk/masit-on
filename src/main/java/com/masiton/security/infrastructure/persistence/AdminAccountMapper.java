package com.masiton.security.infrastructure.persistence;

import com.masiton.security.domain.model.AdminAccount;

/**
 * AdminAccountJpaEntity와 도메인 모델 AdminAccount 사이의 변환만 담당한다.
 */
final class AdminAccountMapper {

    private AdminAccountMapper() {
    }

    static AdminAccount toDomain(AdminAccountJpaEntity entity) {
        return new AdminAccount(
                entity.getId(),
                entity.getLoginId(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static AdminAccountJpaEntity toEntity(AdminAccount adminAccount) {
        return new AdminAccountJpaEntity(
                adminAccount.getId(),
                adminAccount.getLoginId(),
                adminAccount.getPasswordHash(),
                adminAccount.getRole(),
                adminAccount.isActive());
    }
}
