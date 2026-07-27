package com.masiton.security.infrastructure.persistence;

import com.masiton.security.domain.model.ConfirmationToken;

/**
 * ConfirmationTokenJpaEntity와 도메인 모델 ConfirmationToken 사이의 변환만 담당한다.
 */
final class ConfirmationTokenMapper {

    private ConfirmationTokenMapper() {
    }

    static ConfirmationToken toDomain(ConfirmationTokenJpaEntity entity) {
        return new ConfirmationToken(
                entity.getId(),
                entity.getTokenHash(),
                entity.getAdminAccountId(),
                entity.getResourceType(),
                entity.getCandidateSchemaVersion(),
                entity.getIdentityKey(),
                entity.getCandidateSnapshot(),
                entity.getStatus(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getCompletedAt(),
                entity.getResultResourceId());
    }

    static ConfirmationTokenJpaEntity toEntity(ConfirmationToken confirmationToken) {
        return new ConfirmationTokenJpaEntity(
                confirmationToken.getId(),
                confirmationToken.getTokenHash(),
                confirmationToken.getAdminAccountId(),
                confirmationToken.getResourceType(),
                confirmationToken.getCandidateSchemaVersion(),
                confirmationToken.getIdentityKey(),
                confirmationToken.getCandidateSnapshot(),
                confirmationToken.getStatus(),
                confirmationToken.getIssuedAt(),
                confirmationToken.getExpiresAt(),
                confirmationToken.getCompletedAt(),
                confirmationToken.getResultResourceId());
    }
}
