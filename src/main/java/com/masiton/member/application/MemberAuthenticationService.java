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

import com.masiton.common.security.MemberJwtSettings;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;
import com.masiton.member.domain.model.MemberStatus;

@Service
public class MemberAuthenticationService {
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final Duration EMAIL_VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final MemberAccountRepository accounts;
    private final MemberActionTokenRepository actionTokens;
    private final MemberActionTokenDeliveryPort actionTokenDelivery;
    private final MemberSessionStore sessions;
    private final MemberSessionRevocationStore revocations;
    private final MemberTokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final MemberJwtSettings jwtSettings;
    private final Clock clock;

    public MemberAuthenticationService(MemberAccountRepository accounts, MemberActionTokenRepository actionTokens,
            MemberActionTokenDeliveryPort actionTokenDelivery, MemberSessionStore sessions,
            MemberSessionRevocationStore revocations, MemberTokenIssuer tokenIssuer, PasswordEncoder passwordEncoder,
            MemberJwtSettings jwtSettings, Clock memberSessionClock) {
        this.accounts = accounts;
        this.actionTokens = actionTokens;
        this.actionTokenDelivery = actionTokenDelivery;
        this.sessions = sessions;
        this.revocations = revocations;
        this.tokenIssuer = tokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.jwtSettings = jwtSettings;
        this.clock = memberSessionClock;
    }

    @Transactional
    public void register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.equals(password)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        }
        if (accounts.findByEmail(normalizedEmail).isPresent()) {
            return;
        }
        MemberAccount account = accounts.create(normalizedEmail, passwordEncoder.encode(password), Instant.now(clock));
        issueActionToken(account, MemberActionPurpose.EMAIL_VERIFICATION);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        accounts.activate(consume(rawToken, MemberActionPurpose.EMAIL_VERIFICATION).memberId(), Instant.now(clock));
    }

    @Transactional
    public void resendVerification(String email) {
        accounts.findByEmail(normalizeEmail(email))
                .filter(account -> account.status() == MemberStatus.PENDING_VERIFICATION)
                .ifPresent(account -> issueActionToken(account, MemberActionPurpose.EMAIL_VERIFICATION));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        accounts.findByEmail(normalizeEmail(email)).filter(MemberAccount::canAuthenticate)
                .ifPresent(account -> issueActionToken(account, MemberActionPurpose.PASSWORD_RESET));
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        MemberActionToken token = consume(rawToken, MemberActionPurpose.PASSWORD_RESET);
        MemberAccount account = accounts.findById(token.memberId()).orElseThrow(() -> new BusinessException(
                HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_RESET_TOKEN", "The action token is invalid"));
        if (account.email().equals(password)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        }
        Instant now = Instant.now(clock);
        revokeAllSessions(token.memberId().toString(), now);
        accounts.changePassword(token.memberId(), passwordEncoder.encode(password), now);
    }

    public MemberAuthenticationResult login(String email, String password) {
        MemberAccount account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        if (!account.canAuthenticate()) {
            throw invalidCredentials();
        }
        return issueSession(account.id());
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
            revocations.record(new MemberSessionRevocation(
                    UUID.fromString(principal.sessionId()), Instant.now(clock), accessTokenExpiresAt));
            throw authenticationServiceUnavailable();
        }
        if (owner == null) {
            revocations.record(new MemberSessionRevocation(
                    UUID.fromString(principal.sessionId()), Instant.now(clock), accessTokenExpiresAt));
            return;
        }
        if (!principal.memberId().equals(owner.memberId()) || !principal.sessionId().equals(owner.sessionId())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        Instant now = Instant.now(clock);
        try {
            revocations.record(new MemberSessionRevocation(UUID.fromString(owner.sessionId()), now, accessTokenExpiresAt));
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
        actionTokens.replace(new MemberActionToken(account.id(), sha256(rawToken), purpose, now.plus(ttl)), now);
        actionTokenDelivery.send(account.email(), purpose, rawToken);
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

    private BusinessException invalidRefreshToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
    }

    private BusinessException authenticationServiceUnavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "AUTHENTICATION_SERVICE_UNAVAILABLE", "Authentication service is unavailable");
    }

    private void revokeAllSessions(String memberId, Instant now) {
        recordRevocations(sessions.activeSessionIds(memberId), now);
        sessions.revokeAll(memberId);
    }

    private void recordRevocations(java.util.Set<String> sessionIds, Instant now) {
        Instant expiresAt = now.plus(jwtSettings.accessTokenTtl());
        sessionIds.forEach(sessionId -> revocations.record(
                new MemberSessionRevocation(UUID.fromString(sessionId), now, expiresAt)));
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
