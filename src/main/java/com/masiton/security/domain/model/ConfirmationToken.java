package com.masiton.security.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * confirmation_token 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다. candidateSnapshot은 이 골격 단계에서 JSON 원문 문자열로만
 * 보관하며, 역직렬화·검증은 별도 구현체(WS-04)가 담당한다.
 */
public class ConfirmationToken {

    private final UUID id;
    private final byte[] tokenHash;
    private final UUID adminAccountId;
    private final ConfirmationTokenResourceType resourceType;
    private final short candidateSchemaVersion;
    private final String identityKey;
    private final String candidateSnapshot;
    private final ConfirmationTokenStatus status;
    private final OffsetDateTime issuedAt;
    private final OffsetDateTime expiresAt;
    private final OffsetDateTime completedAt;
    private final UUID resultResourceId;

    public ConfirmationToken(
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
