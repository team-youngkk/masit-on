package com.masiton.security.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * confirmation_token 테이블과 매핑되는 JPA Entity다.
 * V3__create_confirmation_token_table.sql의 confirmation_token 테이블 정의와 컬럼이 대응해야 한다.
 * created_at/updated_at 감사 컬럼 구조가 아니라 issued_at만 있으므로 BaseAuditable을 상속하지 않는다.
 * candidate_snapshot(jsonb)은 Hibernate 6/7 표준 방식인
 * {@code @JdbcTypeCode(SqlTypes.JSON)}으로 String 필드를 jsonb 컬럼에 매핑한다.
 */
@Entity
@Table(name = "confirmation_token")
public class ConfirmationTokenJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "admin_account_id", nullable = false, updatable = false)
    private UUID adminAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16, updatable = false)
    private ConfirmationTokenResourceType resourceType;

    @Column(name = "candidate_schema_version", nullable = false, updatable = false)
    private short candidateSchemaVersion;

    @Column(name = "identity_key", nullable = false, length = 128, updatable = false)
    private String identityKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String candidateSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ConfirmationTokenStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "result_resource_id")
    private UUID resultResourceId;

    protected ConfirmationTokenJpaEntity() {
    }

    public ConfirmationTokenJpaEntity(
            UUID id,
            byte[] tokenHash,
            UUID adminAccountId,
            ConfirmationTokenResourceType resourceType,
            short candidateSchemaVersion,
            String identityKey,
            String candidateSnapshot,
            ConfirmationTokenStatus status,
            OffsetDateTime issuedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime completedAt,
            UUID resultResourceId) {
        this.id = id;
        this.tokenHash = tokenHash == null ? null : tokenHash.clone();
        this.adminAccountId = adminAccountId;
        this.resourceType = resourceType;
        this.candidateSchemaVersion = candidateSchemaVersion;
        this.identityKey = identityKey;
        this.candidateSnapshot = candidateSnapshot;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
        this.resultResourceId = resultResourceId;
    }

    public UUID getId() {
        return id;
    }

    public byte[] getTokenHash() {
        return tokenHash == null ? null : tokenHash.clone();
    }

    public UUID getAdminAccountId() {
        return adminAccountId;
    }

    public ConfirmationTokenResourceType getResourceType() {
        return resourceType;
    }

    public short getCandidateSchemaVersion() {
        return candidateSchemaVersion;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public String getCandidateSnapshot() {
        return candidateSnapshot;
    }

    public ConfirmationTokenStatus getStatus() {
        return status;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public UUID getResultResourceId() {
        return resultResourceId;
    }
}
