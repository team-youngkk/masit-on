package com.masiton.security.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import com.masiton.security.application.AuthenticationResult;
import com.masiton.security.application.port.in.LoginAdminUseCase;
import com.masiton.security.application.port.in.LoginAdminUseCase.LoginCommand;
import com.masiton.security.application.port.in.LogoutAdminUseCase;
import com.masiton.security.application.port.in.RefreshAdminTokenUseCase;
import com.masiton.security.infrastructure.configuration.SecurityProperties;
import com.masiton.security.infrastructure.web.AdminClientAddressResolver;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("관리자 인증 Cookie 계약")
class AdminAuthenticationControllerTest {

    private final LoginAdminUseCase loginUseCase = mock(LoginAdminUseCase.class);
    private final RefreshAdminTokenUseCase refreshUseCase = mock(RefreshAdminTokenUseCase.class);
    private final LogoutAdminUseCase logoutUseCase = mock(LogoutAdminUseCase.class);
    private final SecurityProperties securityProperties = securityProperties();
    private final AdminAuthenticationController controller = new AdminAuthenticationController(
            loginUseCase, refreshUseCase, logoutUseCase,
            securityProperties, new AdminClientAddressResolver(securityProperties)
    );

    @Test
    @DisplayName("신뢰한 프록시의 서로 다른 단일 전달 주소를 로그인 출처로 각각 전달한다")
    void 로그인_신뢰프록시_서로다른단일전달주소를각각전달한다() {
        when(loginUseCase.login(any())).thenReturn(new AuthenticationResult("access", "refresh", 1800));

        controller.login(loginRequest(), request("10.0.0.2", "198.51.100.20"));
        controller.login(loginRequest(), request("10.0.0.2", "198.51.100.21"));

        ArgumentCaptor<LoginCommand> commands = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase, times(2)).login(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(LoginCommand::source)
                .containsExactly("198.51.100.20", "198.51.100.21");
    }

    @Test
    @DisplayName("신뢰하지 않은 직접 호출자의 전달 주소는 원격 주소를 사용한다")
    void 로그인_신뢰하지않은peer_전달주소대신원격주소사용() {
        when(loginUseCase.login(any())).thenReturn(new AuthenticationResult("access", "refresh", 1800));

        controller.login(loginRequest(), request("192.0.2.10", "198.51.100.20"));

        verify(loginUseCase).login(argThat(command -> command.source().equals("192.0.2.10")));
    }

    @Test
    @DisplayName("형식이 잘못된 전달 헤더는 원격 주소를 사용한다")
    void 로그인_형식이잘못된전달헤더_원격주소사용() {
        when(loginUseCase.login(any())).thenReturn(new AuthenticationResult("access", "refresh", 1800));

        controller.login(loginRequest(), request("10.0.0.2", "not-an-ip"));

        verify(loginUseCase).login(argThat(command -> command.source().equals("10.0.0.2")));
    }

    @Test
    @DisplayName("여러 값의 전달 헤더는 원격 주소를 사용한다")
    void 로그인_여러값전달헤더_원격주소사용() {
        when(loginUseCase.login(any())).thenReturn(new AuthenticationResult("access", "refresh", 1800));

        controller.login(loginRequest(), request("10.0.0.2", "198.51.100.20, 198.51.100.21"));

        verify(loginUseCase).login(argThat(command -> command.source().equals("10.0.0.2")));
    }

    @Test
    @DisplayName("로그인은 Refresh Cookie를 HttpOnly Secure Strict 인증 경로와 14일 수명으로 발급한다")
    void 로그인_성공_RefreshCookie보안속성을반환한다() {
        when(loginUseCase.login(any())).thenReturn(new AuthenticationResult("access", "refresh", 1800));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        String cookie = controller.login(new AdminAuthenticationController.LoginRequest("admin", "correct-password"), request)
                .getHeaders()
                .getFirst("Set-Cookie");

        assertThat(cookie)
                .contains("masit_on_refresh=refresh", "Path=/api/admin/auth", "Max-Age=1209600")
                .contains("HttpOnly", "Secure", "SameSite=Strict");
        verify(loginUseCase).login(argThat(command -> command.source().equals("203.0.113.10")));
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

    private static SecurityProperties securityProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.getLoginFailure().setReverseProxyEnabled(true);
        properties.getLoginFailure().setTrustedProxyAddresses("10.0.0.2");
        return properties;
    }

    private AdminAuthenticationController.LoginRequest loginRequest() {
        return new AdminAuthenticationController.LoginRequest("admin", "correct-password");
    }

    private MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
