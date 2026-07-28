package com.masiton.security.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.application.port.out.ConfirmationTokenRepositoryPort;
import com.masiton.security.domain.model.ConfirmationToken;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps raw-token handling, hash lookup and completion state transitions in one reusable
 * application boundary. Resource services retain ownership of their duplicate check and entity
 * creation, but perform those operations in the transaction that holds the acquired token lock.
 */
@Service
public class ConfirmationTokenService implements ConfirmationTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationTokenService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final int RAW_TOKEN_BYTES = 32;

    private final ConfirmationTokenRepositoryPort confirmationTokenRepository;
    private final ConfirmationTokenCleanupService confirmationTokenCleanupService;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public ConfirmationTokenService(
            ConfirmationTokenRepositoryPort confirmationTokenRepository,
            ConfirmationTokenCleanupService confirmationTokenCleanupService) {
        this(confirmationTokenRepository, confirmationTokenCleanupService, Clock.systemUTC(), new SecureRandom());
    }

    ConfirmationTokenService(
            ConfirmationTokenRepositoryPort confirmationTokenRepository,
            ConfirmationTokenCleanupService confirmationTokenCleanupService,
            Clock clock,
            SecureRandom secureRandom) {
        this.confirmationTokenRepository = confirmationTokenRepository;
        this.confirmationTokenCleanupService = confirmationTokenCleanupService;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    @Transactional
    public IssuedConfirmationToken issue(ConfirmationTokenIssueCommand command) {
        validateIssue(command);
        cleanExpiredRetentionRecords();

        byte[] rawTokenBytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(rawTokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawTokenBytes);
        OffsetDateTime issuedAt = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = issuedAt.plus(TOKEN_TTL);
        ConfirmationToken token = new ConfirmationToken(
                UUID.randomUUID(),
                sha256(rawToken),
                command.adminAccountId(),
                command.resourceType(),
                command.candidateSchemaVersion(),
                command.identityKey(),
                command.candidateSnapshot(),
                ConfirmationTokenStatus.ISSUED,
                issuedAt,
                expiresAt,
                null,
                null);
        confirmationTokenRepository.save(token);
        return new IssuedConfirmationToken(rawToken, expiresAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AcquiredConfirmationToken acquire(
            String rawToken,
            UUID adminAccountId,
            ConfirmationTokenResourceType expectedResourceType) {
        if (rawToken == null || rawToken.isBlank() || adminAccountId == null || expectedResourceType == null) {
            throw invalidToken();
        }

        ConfirmationToken token = confirmationTokenRepository.findByTokenHashForUpdate(sha256(rawToken))
                .orElseThrow(this::invalidToken);
        if (!token.getAdminAccountId().equals(adminAccountId) || token.getResourceType() != expectedResourceType) {
            throw invalidToken();
        }
        if (token.getStatus() == ConfirmationTokenStatus.ISSUED && !token.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        return new AcquiredConfirmationToken(
                token.getId(),
                token.getCandidateSchemaVersion(),
                token.getIdentityKey(),
                token.getCandidateSnapshot(),
                token.getStatus(),
                token.getResultResourceId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeCreated(UUID tokenId, UUID resourceId) {
        complete(tokenId, resourceId, ConfirmationTokenStatus.CREATED);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeDuplicate(UUID tokenId, UUID resourceId) {
        complete(tokenId, resourceId, ConfirmationTokenStatus.DUPLICATE);
    }

    private void complete(UUID tokenId, UUID resourceId, ConfirmationTokenStatus status) {
        if (tokenId == null || resourceId == null) {
            throw new IllegalArgumentException("Confirmation token and result resource ids are required.");
        }
        if (!confirmationTokenRepository.completeIssuedToken(
                tokenId, status, resourceId, OffsetDateTime.now(clock))) {
            throw new IllegalStateException("Confirmation token was already completed.");
        }
    }

    private void validateIssue(ConfirmationTokenIssueCommand command) {
        Objects.requireNonNull(command, "Confirmation token issue command is required.");
        Objects.requireNonNull(command.adminAccountId(), "Admin account id is required.");
        Objects.requireNonNull(command.resourceType(), "Resource type is required.");
        if (command.candidateSchemaVersion() <= 0) {
            throw new IllegalArgumentException("Candidate schema version must be positive.");
        }
        if (command.identityKey() == null || command.identityKey().isBlank() || command.identityKey().length() > 128) {
            throw new IllegalArgumentException("Identity key must be non-blank and at most 128 characters.");
        }
        if (command.candidateSnapshot() == null || command.candidateSnapshot().isBlank()) {
            throw new IllegalArgumentException("Candidate snapshot is required.");
        }
    }

    private void cleanExpiredRetentionRecords() {
        try {
            confirmationTokenCleanupService.deleteExpiredRetentionRecords();
        } catch (RuntimeException exception) {
            log.warn("confirmation token retention cleanup failed", exception);
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
    }

    private byte[] sha256(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the Java runtime.", exception);
        }
    }
}
