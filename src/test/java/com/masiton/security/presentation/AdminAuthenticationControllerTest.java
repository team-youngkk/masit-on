package com.masiton.security.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import com.masiton.security.application.AuthenticationResult;
import com.masiton.security.application.port.in.LoginAdminUseCase;
import com.masiton.security.application.port.in.LogoutAdminUseCase;
import com.masiton.security.application.port.in.RefreshAdminTokenUseCase;
import com.masiton.security.infrastructure.configuration.SecurityProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("관리자 인증 Cookie 계약")
class AdminAuthenticationControllerTest {

    private final LoginAdminUseCase loginUseCase = mock(LoginAdminUseCase.class);
    private final RefreshAdminTokenUseCase refreshUseCase = mock(RefreshAdminTokenUseCase.class);
    private final LogoutAdminUseCase logoutUseCase = mock(LogoutAdminUseCase.class);
    private final AdminAuthenticationController controller = new AdminAuthenticationController(
            loginUseCase, refreshUseCase, logoutUseCase, new SecurityProperties()
    );

    @Test
    @DisplayName("로그인은 Refresh Cookie를 HttpOnly Secure Strict 인증 경로와 14일 수명으로 발급한다")
    void 로그인_성공_RefreshCookie보안속성을반환한다() {
        when(loginUseCase.login(any())).thenReturn(new AuthenticationResult("access", "refresh", 1800));

        String cookie = controller.login(new AdminAuthenticationController.LoginRequest("admin", "correct-password"))
                .getHeaders()
                .getFirst("Set-Cookie");

        assertThat(cookie)
                .contains("masit_on_refresh=refresh", "Path=/api/admin/auth", "Max-Age=1209600")
                .contains("HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    @DisplayName("로그아웃은 같은 보안 속성의 Refresh Cookie를 즉시 만료한다")
    void 로그아웃_성공_RefreshCookie를만료한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("masit_on_refresh", "refresh"));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin-id");

        String cookie = controller.logout(authentication, request)
                .getHeaders()
                .getFirst("Set-Cookie");

        assertThat(cookie)
                .contains("Path=/api/admin/auth", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
    }
}
