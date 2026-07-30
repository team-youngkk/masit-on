package com.masiton.member.application;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.masiton.common.security.MemberJwtSettings;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.member.application.port.out.MemberDeletionJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;
import com.masiton.member.domain.model.MemberStatus;

@Service
public class MemberAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(MemberAuthenticationService.class);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final Duration EMAIL_VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final MemberAccountRepository accounts;
    private final MemberActionTokenRepository actionTokens;
    private final MemberActionTokenDeliveryPort actionTokenDelivery;
    private final MemberRateLimitStore rateLimits;
    private final MemberDeletionJobStore deletionJobs;
    private final MemberSessionStore sessions;
    private final MemberSessionRevocationRecoveryJobStore revocationRecoveryJobs;
    private final MemberSessionRevocationStore revocations;
    private final MemberTokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final MemberJwtSettings jwtSettings;
    private final Clock clock;

    public MemberAuthenticationService(MemberAccountRepository accounts, MemberActionTokenRepository actionTokens,
            MemberActionTokenDeliveryPort actionTokenDelivery, MemberRateLimitStore rateLimits, MemberDeletionJobStore deletionJobs, MemberSessionStore sessions,
            MemberSessionRevocationRecoveryJobStore revocationRecoveryJobs, MemberSessionRevocationStore revocations,
            MemberTokenIssuer tokenIssuer, PasswordEncoder passwordEncoder,
            MemberJwtSettings jwtSettings, Clock memberSessionClock) {
        this.accounts = accounts;
        this.actionTokens = actionTokens;
        this.actionTokenDelivery = actionTokenDelivery;
        this.rateLimits = rateLimits;
        this.deletionJobs = deletionJobs;
        this.sessions = sessions;
        this.revocationRecoveryJobs = revocationRecoveryJobs;
        this.revocations = revocations;
        this.tokenIssuer = tokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.jwtSettings = jwtSettings;
        this.clock = memberSessionClock;
    }

    @Transactional
    public void register(String email, String password, String source) {
        String normalizedEmail = normalizeEmail(email);
        if (!rateLimits.tryAcquireAccountActionRequest(normalizedEmail, source)) {
            return;
        }
        if (normalizedEmail.equals(password)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        }
        accounts.createIfAbsent(normalizedEmail, passwordEncoder.encode(password), Instant.now(clock))
                .ifPresent(account -> issueActionToken(account, MemberActionPurpose.EMAIL_VERIFICATION));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        accounts.activate(consume(rawToken, MemberActionPurpose.EMAIL_VERIFICATION).memberId(), Instant.now(clock));
    }

    @Transactional
    public void resendVerification(String email, String source) {
        String normalizedEmail = normalizeEmail(email);
        if (!rateLimits.tryAcquireAccountActionRequest(normalizedEmail, source)) {
            return;
        }
        accounts.findByEmail(normalizedEmail)
                .filter(account -> account.status() == MemberStatus.PENDING_VERIFICATION)
                .ifPresent(account -> issueActionToken(account, MemberActionPurpose.EMAIL_VERIFICATION));
    }

    @Transactional
    public void requestPasswordReset(String email, String source) {
        String normalizedEmail = normalizeEmail(email);
        if (!rateLimits.tryAcquireAccountActionRequest(normalizedEmail, source)) {
            return;
        }
        accounts.findByEmail(normalizedEmail).filter(MemberAccount::canAuthenticate)
                .ifPresent(account -> issueActionToken(account, MemberActionPurpose.PASSWORD_RESET));
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        MemberActionToken token = consume(rawToken, MemberActionPurpose.PASSWORD_RESET);
        MemberAccount account = accounts.findByIdForUpdate(token.memberId()).orElseThrow(() -> new BusinessException(
                HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_RESET_TOKEN", "The action token is invalid"));
        if (account.email().equals(password)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        }
        Instant now = Instant.now(clock);
        revokeAllSessions(token.memberId().toString(), now);
        accounts.changePassword(token.memberId(), passwordEncoder.encode(password), now);
    }

    @Transactional
    public MemberAuthenticationResult login(String email, String password, String source) {
        String normalizedEmail = normalizeEmail(email);
        try {
            if (rateLimits.isLoginBlocked(normalizedEmail, source)) {
                throw invalidCredentials();
            }
            MemberAccount account = accounts.findByEmailForUpdate(normalizedEmail)
                    .orElseThrow(() -> invalidCredentials(normalizedEmail, source));
            if (!passwordEncoder.matches(password, account.passwordHash())) {
                throw invalidCredentials(normalizedEmail, source);
            }
            if (!account.canAuthenticate()) {
                throw invalidCredentials(normalizedEmail, source);
            }
            return issueSession(account.id());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw authenticationServiceUnavailable();
        }
    }

    public MemberAuthenticationResult refresh(String refreshToken) {
        try {
            MemberSession session = sessions.rotate(refreshToken, REFRESH_TOKEN_TTL);
            if (revocations.isRevoked(UUID.fromString(session.sessionId()), Instant.now(clock))) {
                sessions.revoke(session.memberId(), session.sessionId());
                throw invalidRefreshToken();
            }
            MemberAccount account = accounts.findById(UUID.fromString(session.memberId())).filter(MemberAccount::canAuthenticate)
                    .orElseThrow(this::invalidRefreshToken);
            return result(account.id(), session);
        } catch (InvalidMemberSessionException exception) {
            recordRevocations(exception.revokedSessionIds(), Instant.now(clock));
            throw invalidRefreshToken();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw authenticationServiceUnavailable();
        }
    }

    public void logout(MemberPrincipal principal, Instant accessTokenExpiresAt, String refreshToken) {
        MemberSessionOwner owner;
        try {
            owner = sessions.findSession(refreshToken).orElse(null);
        } catch (RuntimeException exception) {
            recordRevocation(principal.sessionId(), Instant.now(clock));
            throw authenticationServiceUnavailable();
        }
        if (owner == null) {
            recordRevocation(principal.sessionId(), Instant.now(clock));
            return;
        }
        if (!principal.memberId().equals(owner.memberId()) || !principal.sessionId().equals(owner.sessionId())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        Instant now = Instant.now(clock);
        try {
            recordRevocation(owner.sessionId(), now);
            sessions.revoke(owner.memberId(), owner.sessionId());
        } catch (RuntimeException exception) {
            throw authenticationServiceUnavailable();
        }
    }

    @Transactional
    public void requestDeletion(MemberPrincipal principal, Instant accessTokenExpiresAt) {
        UUID memberId = UUID.fromString(principal.memberId());
        Instant now = Instant.now(clock);
        revokeAllSessions(principal.memberId(), now);
        accounts.requestDeletion(memberId, now);
        deletionJobs.enqueue(memberId, now);
    }

    public MemberAccount currentMember(String memberId) {
        return accounts.findById(UUID.fromString(memberId)).filter(MemberAccount::canAuthenticate)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    private MemberAuthenticationResult issueSession(UUID memberId) {
        MemberSession session = sessions.issue(memberId.toString(), REFRESH_TOKEN_TTL);
        recordRevocations(session.revokedSessionIds(), Instant.now(clock));
        return result(memberId, session);
    }

    private MemberAuthenticationResult result(UUID memberId, MemberSession session) {
        String accessToken = tokenIssuer.issueAccessToken(new MemberPrincipal(memberId.toString(), session.sessionId()));
        return new MemberAuthenticationResult(accessToken, session.refreshToken(), jwtSettings.accessTokenTtl().toSeconds());
    }

    private void issueActionToken(MemberAccount account, MemberActionPurpose purpose) {
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant now = Instant.now(clock);
        Duration ttl = purpose == MemberActionPurpose.EMAIL_VERIFICATION
                ? EMAIL_VERIFICATION_TOKEN_TTL
                : PASSWORD_RESET_TOKEN_TTL;
        try {
            actionTokenDelivery.send(account.email(), purpose, rawToken);
        } catch (RuntimeException exception) {
            log.warn("member action-token mail delivery failed: purpose={}", purpose);
            return;
        }
        actionTokens.replace(new MemberActionToken(account.id(), sha256(rawToken), purpose, now.plus(ttl)), now);
    }

    private MemberActionToken consume(String rawToken, MemberActionPurpose purpose) {
        return actionTokens.consume(rawToken, purpose, Instant.now(clock)).orElseThrow(() -> new BusinessException(
                HttpStatus.BAD_REQUEST,
                purpose == MemberActionPurpose.EMAIL_VERIFICATION
                        ? "INVALID_EMAIL_VERIFICATION_TOKEN"
                        : "INVALID_PASSWORD_RESET_TOKEN",
                "The action token is invalid"
        ));
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    private BusinessException invalidCredentials(String normalizedEmail, String source) {
        rateLimits.recordLoginFailure(normalizedEmail, source);
        return invalidCredentials();
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
    }

    private BusinessException authenticationServiceUnavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "AUTHENTICATION_SERVICE_UNAVAILABLE", "Authentication service is unavailable");
    }

    private void revokeAllSessions(String memberId, Instant now) {
        recordRevocations(sessions.revokeAll(memberId), now);
    }

    private void recordRevocations(java.util.Set<String> sessionIds, Instant now) {
        sessionIds.forEach(sessionId -> recordRevocation(sessionId, now));
    }

    private void recordRevocation(String sessionId, Instant now) {
        MemberSessionRevocation revocation = new MemberSessionRevocation(
                UUID.fromString(sessionId), now, now.plus(REFRESH_TOKEN_TTL));
        try {
            revocations.record(revocation);
        } catch (RuntimeException exception) {
            enqueueRecoveryBestEffort(revocation, now, exception);
            throw exception;
        }
    }

    private void enqueueRecoveryBestEffort(MemberSessionRevocation revocation, Instant now, RuntimeException originalFailure) {
        try {
            revocationRecoveryJobs.enqueue(revocation, now);
        } catch (RuntimeException recoveryFailure) {
            originalFailure.addSuppressed(recoveryFailure);
            log.warn("member session revocation recovery enqueue failed: sessionId={}", revocation.sessionId());
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
