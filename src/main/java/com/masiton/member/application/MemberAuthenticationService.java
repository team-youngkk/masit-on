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
    private static final Duration ACTION_TOKEN_TTL = Duration.ofHours(1);

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
        if (accounts.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "MEMBER_EMAIL_ALREADY_REGISTERED", "Email is already registered");
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
        accounts.changePassword(token.memberId(), passwordEncoder.encode(password), Instant.now(clock));
        sessions.revokeAll(token.memberId().toString());
    }

    public MemberAuthenticationResult login(String email, String password) {
        MemberAccount account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (account.status() == MemberStatus.PENDING_VERIFICATION) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "MEMBER_EMAIL_NOT_VERIFIED", "Email verification is required");
        }
        if (!account.canAuthenticate()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "MEMBER_ACCOUNT_UNAVAILABLE", "Account is unavailable");
        }
        return issueSession(account.id());
    }

    public MemberAuthenticationResult refresh(String refreshToken) {
        try {
            MemberSession session = sessions.rotate(refreshToken, REFRESH_TOKEN_TTL);
            MemberAccount account = accounts.findById(UUID.fromString(session.memberId())).filter(MemberAccount::canAuthenticate)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
            return result(account.id(), session);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    public void logout(String memberId, String refreshToken) {
        if (!sessions.matches(memberId, refreshToken)) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        sessions.revokeAll(memberId);
    }

    @Transactional
    public void requestDeletion(MemberPrincipal principal, Instant accessTokenExpiresAt) {
        UUID memberId = UUID.fromString(principal.memberId());
        Instant now = Instant.now(clock);
        accounts.requestDeletion(memberId, now);
        sessions.revokeAll(principal.memberId());
        revocations.record(new MemberSessionRevocation(UUID.fromString(principal.sessionId()), now, accessTokenExpiresAt));
    }

    public MemberAccount currentMember(String memberId) {
        return accounts.findById(UUID.fromString(memberId)).filter(MemberAccount::canAuthenticate)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    private MemberAuthenticationResult issueSession(UUID memberId) {
        return result(memberId, sessions.issue(memberId.toString(), REFRESH_TOKEN_TTL));
    }

    private MemberAuthenticationResult result(UUID memberId, MemberSession session) {
        String accessToken = tokenIssuer.issueAccessToken(new MemberPrincipal(memberId.toString(), session.sessionId()));
        return new MemberAuthenticationResult(accessToken, session.refreshToken(), jwtSettings.accessTokenTtl().toSeconds());
    }

    private void issueActionToken(MemberAccount account, MemberActionPurpose purpose) {
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant now = Instant.now(clock);
        actionTokens.replace(new MemberActionToken(account.id(), sha256(rawToken), purpose, now.plus(ACTION_TOKEN_TTL)), now);
        actionTokenDelivery.send(account.email(), purpose, rawToken);
    }

    private MemberActionToken consume(String rawToken, MemberActionPurpose purpose) {
        return actionTokens.consume(rawToken, purpose, Instant.now(clock))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "MEMBER_ACTION_TOKEN_INVALID", "Action token is invalid"));
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
