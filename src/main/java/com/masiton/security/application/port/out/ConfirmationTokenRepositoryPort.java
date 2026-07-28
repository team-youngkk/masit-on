package com.masiton.security.application.port.out;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import com.masiton.security.domain.model.ConfirmationToken;
import com.masiton.security.domain.model.ConfirmationTokenStatus;

/**
 * Application이 ConfirmationToken 영속성에 요구하는 계약이다.
 * Infrastructure Adapter가 구현하며, Application은 이 인터페이스만 의존한다.
 */
public interface ConfirmationTokenRepositoryPort {

    ConfirmationToken save(ConfirmationToken confirmationToken);

    Optional<ConfirmationToken> findById(UUID id);

    /**
     * Returns the token while holding a PostgreSQL row lock. Callers must already be in the
     * resource-creation transaction so that validation, resource creation and completion share
     * one commit boundary.
     */
    Optional<ConfirmationToken> findByTokenHashForUpdate(byte[] tokenHash);

    /**
     * Completes an issued token exactly once. The status predicate is a final guard in addition
     * to the row lock held by the application transaction.
     */
    boolean completeIssuedToken(
            UUID tokenId,
            ConfirmationTokenStatus status,
            UUID resultResourceId,
            OffsetDateTime completedAt);

    int deleteExpiredRetentionRecords(OffsetDateTime retentionDeadline, int limit);
}
