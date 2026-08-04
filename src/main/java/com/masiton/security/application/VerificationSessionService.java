package com.masiton.security.application;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.security.application.port.out.VerificationAccessStore;
import com.masiton.security.application.port.out.VerificationCredentialVerifier;
import com.masiton.security.application.port.out.VerificationSessionSettings;
import com.masiton.security.application.port.out.VerificationStoreUnavailableException;

@Service
public class VerificationSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final VerificationAccessStore store;
    private final VerificationSessionSettings settings;
    private final VerificationCredentialVerifier credentialVerifier;

    public VerificationSessionService(
            VerificationAccessStore store,
            VerificationSessionSettings settings,
            VerificationCredentialVerifier credentialVerifier
    ) {
        this.store = store;
        this.settings = settings;
        this.credentialVerifier = credentialVerifier;
    }

    public String create(String loginId, String password, String source) {
        try {
            if (store.isBlocked(loginId, source)) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "로그인 시도가 너무 많습니다.", settings.failureTtl().toSeconds());
            }
            if (!credentialVerifier.matches(loginId, password)) {
                store.recordFailure(loginId, source);
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_VALIDATION_CREDENTIALS",
                        "로그인 정보를 확인할 수 없습니다.");
            }
            store.clearFailures(loginId, source);
            byte[] token = new byte[32];
            SECURE_RANDOM.nextBytes(token);
            String rawSessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
            store.save(rawSessionId, settings.sessionTtl());
            return rawSessionId;
        } catch (BusinessException exception) {
            throw exception;
        } catch (VerificationStoreUnavailableException exception) {
            throw unavailable();
        }
    }

    public void revoke(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) return;
        try {
            store.delete(rawSessionId);
        } catch (VerificationStoreUnavailableException exception) {
            throw unavailable();
        }
    }

    public boolean isValid(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) return false;
        try {
            return store.exists(rawSessionId);
        } catch (VerificationStoreUnavailableException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "VALIDATION_SESSION_UNAVAILABLE",
                "검증 세션을 확인할 수 없습니다.");
    }
}
