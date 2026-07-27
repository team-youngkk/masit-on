package com.masiton.security.application;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.security.application.port.in.LoginAdminUseCase.LoginCommand;
import com.masiton.security.application.port.out.AdminCredentialVerifier;
import com.masiton.security.application.port.out.LoginFailureStore;
import com.masiton.security.application.port.out.RefreshTokenStore;
import com.masiton.security.application.port.out.TokenIssuer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("관리자 인증 애플리케이션 서비스")
class AdminAuthenticationServiceTest {

    private final AdminCredentialVerifier credentialVerifier = mock(AdminCredentialVerifier.class);
    private final LoginFailureStore loginFailureStore = mock(LoginFailureStore.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final AdminAuthenticationService service = new AdminAuthenticationService(
            credentialVerifier,
            loginFailureStore,
            refreshTokenStore,
            tokenIssuer,
            new SecurityTokenLifetime(Duration.ofMinutes(30), Duration.ofDays(14))
    );

    @Test
    @DisplayName("활성 관리자 자격 증명이 맞으면 Access와 Refresh Token을 발급한다")
    void 로그인_활성관리자_토큰발급() {
        AdminPrincipal principal = new AdminPrincipal("admin-id", Set.of(AdminRole.ADMIN));
        when(credentialVerifier.authenticate("admin", "correct-password")).thenReturn(Optional.of(principal));
        when(refreshTokenStore.issue("admin-id", Duration.ofDays(14)))
                .thenReturn(new RefreshTokenRotation("admin-id", "refresh-token"));
        when(tokenIssuer.issueAccessToken(principal)).thenReturn("access-token");

        AuthenticationResult result = service.login(new LoginCommand(" admin ", "correct-password"));

        assertThat(result).isEqualTo(new AuthenticationResult("access-token", "refresh-token", 1800));
        verify(loginFailureStore).clear("admin");
    }

    @Test
    @DisplayName("잘못된 자격 증명은 동일한 401로 실패 횟수만 기록한다")
    void 로그인_잘못된자격증명_실패기록후401() {
        when(credentialVerifier.authenticate("unknown", "wrong-password")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("unknown", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.name());

        verify(loginFailureStore).recordFailure("unknown");
        verify(refreshTokenStore, never()).issue(any(), any());
    }

    @Test
    @DisplayName("재사용되었거나 유효하지 않은 Refresh Token은 401로 변환한다")
    void 재발급_유효하지않은RefreshToken_401() {
        when(refreshTokenStore.rotate(eq("replayed-token"), any()))
                .thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> service.refresh("replayed-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.name());
    }

    @Test
    @DisplayName("Refresh Token 저장소 장애는 새 Access Token 없이 401로 차단한다")
    void 재발급_Redis장애_401로차단한다() {
        when(refreshTokenStore.rotate(eq("refresh-token"), any()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> service.refresh("refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.name());
        verify(tokenIssuer, never()).issueAccessToken(any());
    }

    @Test
    @DisplayName("로그아웃은 현재 관리자와 일치하는 Refresh Token만 폐기한다")
    void 로그아웃_현재관리자RefreshToken일치_폐기() {
        when(refreshTokenStore.matches("admin-id", "refresh-token")).thenReturn(true);

        service.logout("admin-id", "refresh-token");

        verify(refreshTokenStore).revoke("admin-id");
    }
}
