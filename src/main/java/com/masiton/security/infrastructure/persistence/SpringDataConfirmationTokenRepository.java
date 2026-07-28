package com.masiton.security.infrastructure.persistence;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.masiton.security.domain.model.ConfirmationTokenStatus;

/**
 * Infrastructure 내부 전용 Spring Data Repository다.
 * Application이나 다른 도메인에서 직접 주입하지 않는다.
 */
interface SpringDataConfirmationTokenRepository extends JpaRepository<ConfirmationTokenJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from ConfirmationTokenJpaEntity token where token.tokenHash = :tokenHash")
    Optional<ConfirmationTokenJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") byte[] tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ConfirmationTokenJpaEntity token
               set token.status = :status,
                   token.resultResourceId = :resultResourceId,
                   token.completedAt = :completedAt
             where token.id = :tokenId
               and token.status = com.masiton.security.domain.model.ConfirmationTokenStatus.ISSUED
            """)
    int completeIssuedToken(
            @Param("tokenId") UUID tokenId,
            @Param("status") ConfirmationTokenStatus status,
            @Param("resultResourceId") UUID resultResourceId,
            @Param("completedAt") OffsetDateTime completedAt);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            delete from confirmation_token
             where id in (
                 select id
                   from confirmation_token
                  where (status = 'ISSUED' and expires_at <= :retentionDeadline)
                     or (status in ('CREATED', 'DUPLICATE') and completed_at <= :retentionDeadline)
                  order by issued_at
                  limit :limit
             )
            """, nativeQuery = true)
    int deleteExpiredRetentionRecords(
            @Param("retentionDeadline") OffsetDateTime retentionDeadline,
            @Param("limit") int limit);
}
