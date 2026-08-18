package com.masiton.member.presentation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.security.MemberCookieSettings;
import com.masiton.common.security.MemberJwtSettings;
import com.masiton.common.web.BusinessException;
import com.masiton.member.application.MemberAuthenticationResult;
import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.MemberSessionOwner;
import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.infrastructure.configuration.MemberRateLimitProperties;
import com.masiton.member.infrastructure.web.MemberClientAddressResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("회원 인증 API 응답 계약")
class MemberAuthenticationControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    private final MemberAuthenticationService service = mock(MemberAuthenticationService.class);
    private final MemberCookieSettings cookieSettings = new MemberCookieSettings(
            "__Secure-masiton-refresh",
            Duration.ofDays(14),
            "/api/auth/tokens",
            true,
            "Strict",
            "https://example.test"
    );
    private final MemberAuthenticationController controller = new MemberAuthenticationController(
            service,
            cookieSettings,
            addressResolver()
    );
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    @DisplayName("회원가입은 상태 비노출 접수 본문을 반환한다")
    void 회원가입_유효요청_접수본문반환() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        var response = controller.register(new MemberAuthenticationController.CredentialsRequest(
                "member@example.com",
                "correct horse battery staple"
        ), request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isEqualTo(new MemberAuthenticationController.AcceptedResponse(true));
        verify(service).register("member@example.com", "correct horse battery staple", "127.0.0.1");
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 newPassword 필드를 서비스에 전달한다")
    void 비밀번호재설정확인_newPassword전달() {
        var response = controller.resetPassword(new MemberAuthenticationController.ResetPasswordRequest(
                "reset-token",
                "new correct horse battery staple"
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).resetPassword("reset-token", "new correct horse battery staple");
    }

    @Test
    @DisplayName("이메일 인증은 요청 출처를 전달하고 429의 Retry-After를 보존한다")
    void 이메일인증_출처전달과RetryAfter보존() throws Exception {
        doThrow(new BusinessException(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Too many email verification attempts",
                600
        )).when(service).verifyEmail("ab7k9m2q", "127.0.0.1");

        mockMvc.perform(post("/api/auth/email-verifications")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"ab7k9m2q"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "600"));

        verify(service).verifyEmail("ab7k9m2q", "127.0.0.1");
    }

    @Test
    @DisplayName("이메일 인증은 token 필드가 없어도 서비스에 null을 전달해 제한을 소모하게 한다")
    void 이메일인증_token필드누락_서비스에null전달() throws Exception {
        doThrow(new BusinessException(
                com.masiton.common.web.ErrorCode.MISSING_REQUIRED_FIELD, "token", "필수 요청 값이 누락되었습니다."
        )).when(service).verifyEmail(null, "127.0.0.1");

        mockMvc.perform(post("/api/auth/email-verifications")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"));

        verify(service).verifyEmail(null, "127.0.0.1");
    }

    @Test
    @DisplayName("로그인은 Refresh Cookie를 보안 속성과 함께 발급한다")
    void 로그인_성공_RefreshCookie계약반환() {
        when(service.login(any(), any(), any())).thenReturn(new MemberAuthenticationResult("access", "refresh", 1800));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        String cookie = controller.login(new MemberAuthenticationController.CredentialsRequest(
                        "member@example.com",
                        "correct horse battery staple"
                ), request)
                .getHeaders()
                .getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains("__Secure-masiton-refresh=refresh", "Path=/api/auth/tokens", "Max-Age=1209600")
                .contains("HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    @DisplayName("로그인 실패는 동일한 인증 오류를 반환하고 Refresh Cookie를 발급하지 않는다")
    void 로그인_인증실패_401과Cookie미발급() throws Exception {
        when(service.login(any(), any(), any())).thenThrow(new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password"
        ));

        mockMvc.perform(post("/api/auth/tokens")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"any-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 Refresh 요청은 403이고 Cookie를 건드리지 않는다")
    void refresh_다른Origin_403과Cookie미변경() throws Exception {
        mockMvc.perform(post("/api/auth/tokens/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .cookie(new Cookie(cookieSettings.cookieName(), "refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Origin 헤더가 여러 개인 회원 Refresh 요청은 403이고 서비스를 호출하지 않는다")
    void refresh_다중Origin_403과서비스미호출() throws Exception {
        mockMvc.perform(post("/api/auth/tokens/refresh")
                        .header(HttpHeaders.ORIGIN, cookieSettings.publicBaseUrl())
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .cookie(new Cookie(cookieSettings.cookieName(), "refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("회원 Refresh는 대소문자와 기본 포트가 다른 동등 Origin을 허용한다")
    void refresh_동등한Origin_허용() throws Exception {
        MemberCookieSettings canonicalSettings = memberCookieSettings("HTTPS://EXAMPLE.TEST:443");
        MemberAuthenticationService canonicalService = mock(MemberAuthenticationService.class);
        when(canonicalService.refresh("refresh-token"))
                .thenReturn(new MemberAuthenticationResult("access", "refresh", 1800));
        MockMvc canonicalMockMvc = MockMvcBuilders.standaloneSetup(new MemberAuthenticationController(
                canonicalService,
                canonicalSettings,
                addressResolver()
        )).build();

        canonicalMockMvc.perform(post("/api/auth/tokens/refresh")
                        .header(HttpHeaders.ORIGIN, "https://example.test")
                        .cookie(new Cookie(canonicalSettings.cookieName(), "refresh-token")))
                .andExpect(status().isOk());

        verify(canonicalService).refresh("refresh-token");
    }

    @Test
    @DisplayName("회원 Refresh는 통합 Origin allowlist의 두 번째 Origin도 허용한다")
    void refresh_복수AllowedOrigins_두번째Origin허용() throws Exception {
        MemberCookieSettings allowlistSettings = memberCookieSettings(
                "https://first.example.test, HTTPS://SECOND.EXAMPLE.TEST:443");
        MemberAuthenticationService allowlistService = mock(MemberAuthenticationService.class);
        when(allowlistService.refresh("refresh-token"))
                .thenReturn(new MemberAuthenticationResult("access", "refresh", 1800));
        MockMvc allowlistMockMvc = MockMvcBuilders.standaloneSetup(new MemberAuthenticationController(
                allowlistService,
                allowlistSettings,
                addressResolver()
        )).build();

        allowlistMockMvc.perform(post("/api/auth/tokens/refresh")
                        .header(HttpHeaders.ORIGIN, "https://second.example.test")
                        .cookie(new Cookie(allowlistSettings.cookieName(), "refresh-token")))
                .andExpect(status().isOk());

        verify(allowlistService).refresh("refresh-token");
    }

    @Test
    @DisplayName("회원 공개 Origin 설정에 경로가 포함되면 즉시 실패한다")
    void 회원공개Origin_경로포함_설정실패() {
        assertThatIllegalStateException()
                .isThrownBy(() -> memberCookieSettings("https://example.test/path"));
    }

    @Test
    @DisplayName("Refresh Token이 유효하지 않으면 Refresh Cookie를 즉시 만료한다")
    void refresh_무효Token_만료Cookie반환() throws Exception {
        when(service.refresh("refresh-token")).thenThrow(new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "Refresh token is invalid"
        ));

        mockMvc.perform(post("/api/auth/tokens/refresh")
                        .header(HttpHeaders.ORIGIN, cookieSettings.publicBaseUrl())
                        .cookie(new Cookie(cookieSettings.cookieName(), "refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(cookieSettings.cookieName() + "="),
                        org.hamcrest.Matchers.containsString("Path=" + cookieSettings.path()),
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                )));
    }

    @Test
    @DisplayName("로그아웃에서 Refresh Cookie가 없으면 만료 Cookie를 반환한다")
    void 로그아웃_RefreshCookie누락_만료Cookie반환() throws Exception {
        mockMvc.perform(delete("/api/auth/tokens")
                        .header(HttpHeaders.ORIGIN, cookieSettings.publicBaseUrl())
                        .principal(authentication()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(cookieSettings.cookieName() + "="),
                        org.hamcrest.Matchers.containsString("Path=" + cookieSettings.path()),
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                )));
    }

    @Test
    @DisplayName("로그아웃의 서버 오류도 Refresh Cookie를 즉시 만료한다")
    void 로그아웃_폐기표식저장실패_500과만료Cookie반환() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MemberSessionStore actualSessions = mock(MemberSessionStore.class);
        MemberSessionRevocationStore actualRevocations = mock(MemberSessionRevocationStore.class);
        MemberSessionRevocationRecoveryJobStore actualRecoveryJobs =
                mock(MemberSessionRevocationRecoveryJobStore.class);
        when(actualSessions.findSession("refresh-token"))
                .thenReturn(Optional.of(new MemberSessionOwner(memberId.toString(), sessionId.toString())));
        doThrow(new IllegalStateException("database unavailable")).when(actualRevocations)
                .record(any(MemberSessionRevocation.class));
        MemberAuthenticationController actualController = new MemberAuthenticationController(
                actualService(actualSessions, actualRevocations, actualRecoveryJobs),
                cookieSettings,
                addressResolver()
        );
        MockMvc actualMockMvc = MockMvcBuilders.standaloneSetup(actualController).build();

        actualMockMvc.perform(delete("/api/auth/tokens")
                        .header(HttpHeaders.ORIGIN, cookieSettings.publicBaseUrl())
                        .cookie(new Cookie(cookieSettings.cookieName(), "refresh-token"))
                        .principal(authentication(memberId.toString(), sessionId.toString())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(cookieSettings.cookieName() + "="),
                        org.hamcrest.Matchers.containsString("Path=" + cookieSettings.path()),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict")
                )));
        verify(actualRecoveryJobs).enqueue(
                new MemberSessionRevocation(
                        sessionId,
                        NOW,
                        NOW.plus(Duration.ofDays(14))
                ),
                NOW
        );
    }

    @Test
    @DisplayName("로그아웃 성공은 Refresh Cookie를 즉시 만료한다")
    void 로그아웃_성공_만료Cookie반환() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ORIGIN, cookieSettings.publicBaseUrl());
        request.setCookies(new Cookie(cookieSettings.cookieName(), "refresh-token"));

        String cookie = controller.logout(authentication(), request)
                .getHeaders()
                .getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains(cookieSettings.cookieName() + "=")
                .contains("Path=" + cookieSettings.path(), "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    @DisplayName("Origin 헤더가 여러 개인 회원 로그아웃은 403이고 서비스를 호출하지 않는다")
    void 로그아웃_다중Origin_403과서비스미호출() throws Exception {
        mockMvc.perform(delete("/api/auth/tokens")
                        .header(HttpHeaders.ORIGIN, cookieSettings.publicBaseUrl())
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .cookie(new Cookie(cookieSettings.cookieName(), "refresh-token"))
                        .principal(authentication()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("회원 로그아웃은 대소문자와 기본 포트가 다른 동등 Origin을 허용한다")
    void 로그아웃_동등한Origin_허용() throws Exception {
        MemberCookieSettings canonicalSettings = memberCookieSettings("HTTPS://EXAMPLE.TEST:443");
        MemberAuthenticationService canonicalService = mock(MemberAuthenticationService.class);
        MockMvc canonicalMockMvc = MockMvcBuilders.standaloneSetup(new MemberAuthenticationController(
                canonicalService,
                canonicalSettings,
                addressResolver()
        )).build();

        canonicalMockMvc.perform(delete("/api/auth/tokens")
                        .header(HttpHeaders.ORIGIN, "https://example.test")
                        .cookie(new Cookie(canonicalSettings.cookieName(), "refresh-token"))
                        .principal(authentication()))
                .andExpect(status().isNoContent());

        verify(canonicalService).logout(any(), any(), any());
    }

    private static MemberCookieSettings memberCookieSettings(String publicBaseUrl) {
        return new MemberCookieSettings(
                "__Secure-masiton-refresh",
                Duration.ofDays(14),
                "/api/auth/tokens",
                true,
                "Strict",
                publicBaseUrl
        );
    }

    private static MemberClientAddressResolver addressResolver() {
        MemberRateLimitProperties properties = new MemberRateLimitProperties();
        properties.setSecret("test-secret");
        return new MemberClientAddressResolver(properties);
    }

    private JwtAuthenticationToken authentication() {
        return authentication("member-id", "session-id");
    }

    private JwtAuthenticationToken authentication(String memberId, String sessionId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(1800),
                Map.of("alg", "none"),
                Map.of("sub", memberId, "sid", sessionId)
        );
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("MEMBER")));
    }

    private MemberAuthenticationService actualService(
            MemberSessionStore actualSessions,
            MemberSessionRevocationStore actualRevocations,
            MemberSessionRevocationRecoveryJobStore actualRecoveryJobs
    ) {
        return new MemberAuthenticationService(
                mock(com.masiton.member.application.port.out.MemberAccountRepository.class),
                mock(com.masiton.member.application.port.out.MemberActionTokenRepository.class),
                mock(com.masiton.member.application.port.out.MemberActionMailOutboxStore.class),
                mock(com.masiton.member.application.port.out.MemberActionTokenCipher.class),
                mock(com.masiton.member.application.port.out.MemberRateLimitStore.class),
                mock(com.masiton.member.application.port.out.MemberDeletionJobStore.class),
                actualSessions,
                actualRecoveryJobs,
                actualRevocations,
                mock(com.masiton.member.application.port.out.MemberTokenIssuer.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                new MemberJwtSettings("issuer", "audience", Duration.ofMinutes(30), "key-id"),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
