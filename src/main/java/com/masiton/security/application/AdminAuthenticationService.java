package com.masiton.security.application;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.security.application.port.in.LoginAdminUseCase;
import com.masiton.security.application.port.in.LogoutAdminUseCase;
import com.masiton.security.application.port.in.RefreshAdminTokenUseCase;
import com.masiton.security.application.port.out.AdminCredentialVerifier;
import com.masiton.security.application.port.out.LoginFailureStore;
import com.masiton.security.application.port.out.RefreshTokenStore;
import com.masiton.security.application.port.out.TokenIssuer;

/**
 * Coordinates credentials, signed access tokens, and stateful refresh tokens through ports.
 */
@Service
public class AdminAuthenticationService implements LoginAdminUseCase, RefreshAdminTokenUseCase, LogoutAdminUseCase {

    private final AdminCredentialVerifier credentialVerifier;
    private final LoginFailureStore loginFailureStore;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenIssuer tokenIssuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public AdminAuthenticationService(
            AdminCredentialVerifier credentialVerifier,
            LoginFailureStore loginFailureStore,
            RefreshTokenStore refreshTokenStore,
            TokenIssuer tokenIssuer,
            SecurityTokenLifetime securityTokenLifetime
    ) {
        this.credentialVerifier = credentialVerifier;
        this.loginFailureStore = loginFailureStore;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenIssuer = tokenIssuer;
        this.accessTokenTtl = securityTokenLifetime.accessTokenTtl();
        this.refreshTokenTtl = securityTokenLifetime.refreshTokenTtl();
    }

    @Override
    public AuthenticationResult login(LoginCommand command) {
        String loginId = command.loginId().trim();
        if (loginFailureStore.isBlocked(loginId)) {
            loginFailureStore.recordFailure(loginId);
            throw authenticationRequired();
        }

        AdminPrincipal principal = credentialVerifier.authenticate(loginId, command.password())
                .orElseThrow(() -> {
                    loginFailureStore.recordFailure(loginId);
                    return authenticationRequired();
                });
        loginFailureStore.clear(loginId);
        RefreshTokenRotation rotation = refreshTokenStore.issue(principal.adminId(), refreshTokenTtl);
        return result(principal, rotation.refreshToken());
    }

    @Override
    public AuthenticationResult refresh(String refreshToken) {
        RefreshTokenRotation rotation;
        try {
            rotation = refreshTokenStore.rotate(refreshToken, refreshTokenTtl);
        } catch (RuntimeException exception) {
            // Redis 검증·회전이 불가능하면 새 Access Token을 발급하지 않는 fail-closed 정책이다.
            throw authenticationRequired();
        }
        AdminPrincipal principal = credentialVerifier.findActivePrincipalById(rotation.adminId())
                .orElseThrow(() -> {
                    refreshTokenStore.revoke(rotation.adminId());
                    return authenticationRequired();
                });
        return result(principal, rotation.refreshToken());
    }

    @Override
    public void logout(String adminId, String refreshToken) {
        if (!refreshTokenStore.matches(adminId, refreshToken)) {
            throw authenticationRequired();
        }
        refreshTokenStore.revoke(adminId);
    }

    private AuthenticationResult result(AdminPrincipal principal, String refreshToken) {
        return new AuthenticationResult(
                tokenIssuer.issueAccessToken(principal),
                refreshToken,
                accessTokenTtl.toSeconds()
        );
    }

    private BusinessException authenticationRequired() {
        return new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
