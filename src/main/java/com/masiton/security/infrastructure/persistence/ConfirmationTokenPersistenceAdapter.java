package com.masiton.security.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.ConfirmationTokenRepositoryPort;
import com.masiton.security.domain.model.ConfirmationToken;
import com.masiton.security.domain.model.ConfirmationTokenStatus;

/**
 * ConfirmationTokenRepositoryPort의 JPA 구현체다. JPA Entity와 도메인 모델 변환은
 * ConfirmationTokenMapper에 위임한다.
 */
@Component
class ConfirmationTokenPersistenceAdapter implements ConfirmationTokenRepositoryPort {

    private final SpringDataConfirmationTokenRepository springDataConfirmationTokenRepository;

    ConfirmationTokenPersistenceAdapter(
            SpringDataConfirmationTokenRepository springDataConfirmationTokenRepository) {
        this.springDataConfirmationTokenRepository = springDataConfirmationTokenRepository;
    }

    @Override
    public ConfirmationToken save(ConfirmationToken confirmationToken) {
        ConfirmationTokenJpaEntity savedEntity = springDataConfirmationTokenRepository.save(
                ConfirmationTokenMapper.toEntity(confirmationToken));
        return ConfirmationTokenMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<ConfirmationToken> findById(UUID id) {
        return springDataConfirmationTokenRepository.findById(id)
                .map(ConfirmationTokenMapper::toDomain);
    }

    @Override
    public Optional<ConfirmationToken> findByTokenHashForUpdate(byte[] tokenHash) {
        return springDataConfirmationTokenRepository.findByTokenHashForUpdate(tokenHash)
                .map(ConfirmationTokenMapper::toDomain);
    }

    @Override
    public boolean completeIssuedToken(
            UUID tokenId,
            ConfirmationTokenStatus status,
            UUID resultResourceId,
            OffsetDateTime completedAt) {
        return springDataConfirmationTokenRepository.completeIssuedToken(
                tokenId,
                status,
                resultResourceId,
                completedAt) == 1;
    }

    @Override
    public int deleteExpiredRetentionRecords(OffsetDateTime retentionDeadline, int limit) {
        return springDataConfirmationTokenRepository.deleteExpiredRetentionRecords(retentionDeadline, limit);
    }
}
