package com.masiton.security.infrastructure.web;

import java.io.IOException;
import java.time.Duration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.security.MemberCookieSettings;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;

@DisplayName("회원 보안 오류 응답 계약")
class SecurityErrorWriterTest {

    private final MemberCookieSettings cookieSettings = new MemberCookieSettings(
            "__Secure-masiton-refresh",
            Duration.ofDays(14),
            "/api/auth/tokens",
            true,
            "Strict",
            "https://example.test"
    );
    private final SecurityErrorWriter writer = new SecurityErrorWriter(new ObjectMapper(), cookieSettings);

    @Test
    @DisplayName("api me 인증 오류는 private no-store를 반환한다")
    void apiMe_인증오류_privateNoStore반환() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.commence(request, response, new InsufficientAuthenticationException("missing"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("api me 상태 저장소 장애는 private no-store를 반환한다")
    void apiMe_상태저장소장애_privateNoStore반환() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.authenticationServiceUnavailable(request, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.code")).isEqualTo("AUTHENTICATION_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("회원 로그아웃의 상태 저장소 장애는 Refresh Cookie를 만료한다")
    void 회원로그아웃_상태저장소장애_RefreshCookie만료() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/auth/tokens");
        request.setAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE, "trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.authenticationServiceUnavailable(request, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(cookieSettings.cookieName() + "=")
                .contains("Path=" + cookieSettings.path(), "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
    }
}
