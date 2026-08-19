package com.masiton.security.infrastructure.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.masiton.common.security.MemberCookieSettings;
import com.masiton.common.security.MemberSessionAccessChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;

@DisplayName("회원 세션 폐기 필터")
class MemberSessionRevocationFilterTest {

    private final MemberSessionAccessChecker accessChecker = mock(MemberSessionAccessChecker.class);
    private final MemberCookieSettings cookieSettings = new MemberCookieSettings(
            "__Secure-masiton-refresh",
            Duration.ofDays(14),
            "/api/auth/tokens",
            true,
            "Strict",
            "https://example.test"
    );
    private final SecurityErrorWriter errorWriter = new SecurityErrorWriter(new ObjectMapper(), cookieSettings);
    private final MemberSessionRevocationFilter filter = new MemberSessionRevocationFilter(accessChecker, errorWriter);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그아웃의 세션 상태 저장소 장애는 필터에서 503과 만료 Cookie를 반환한다")
    void 로그아웃_세션상태저장소장애_503과만료Cookie반환() throws Exception {
        String memberId = "member-id";
        String sessionId = "session-id";
        when(accessChecker.check(memberId, sessionId, "MEMBER"))
                .thenReturn(MemberSessionAccessChecker.AccessDecision.UNAVAILABLE);
        SecurityContextHolder.getContext().setAuthentication(authentication(memberId, sessionId));
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/auth/tokens");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(cookieSettings.cookieName() + "=")
                .contains("Path=" + cookieSettings.path(), "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("관리자 API는 현재 ADMIN 역할과 활성 세션이 일치할 때 통과한다")
    void 관리자API_ADMIN역할과세션일치_통과() throws Exception {
        when(accessChecker.check("admin-id", "session-id", "ADMIN"))
                .thenReturn(MemberSessionAccessChecker.AccessDecision.ALLOWED);
        SecurityContextHolder.getContext().setAuthentication(authentication("admin-id", "session-id", "ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/restaurants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("현재 DB 역할이나 상태가 일치하지 않으면 보호 API를 401로 거부한다")
    void 보호API_현재역할상태불일치_401() throws Exception {
        when(accessChecker.check("member-id", "session-id", "MEMBER"))
                .thenReturn(MemberSessionAccessChecker.AccessDecision.DENIED);
        SecurityContextHolder.getContext().setAuthentication(authentication("member-id", "session-id", "MEMBER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("인기 맛집 공개 조회는 유효한 회원 인증이 있어도 세션 상태 저장소를 조회하지 않는다")
    void 인기맛집공개조회_유효한회원인증_세션상태저장소를조회하지않는다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication("member-id", "session-id"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/popular");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(accessChecker);
    }

    @Test
    @DisplayName("지도 맛집 좌표 공개 조회는 유효한 회원 인증이 있어도 세션 상태 저장소를 조회하지 않는다")
    void 지도좌표공개조회_유효한회원인증_세션상태저장소를조회하지않는다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication("member-id", "session-id"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/map-points");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(accessChecker);
    }

    private JwtAuthenticationToken authentication(String memberId, String sessionId) {
        return authentication(memberId, sessionId, "MEMBER");
    }

    private JwtAuthenticationToken authentication(String memberId, String sessionId, String role) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(1800),
                Map.of("alg", "none"),
                Map.of("sub", memberId, "sid", sessionId, "roles", java.util.List.of(role))
        );
        return new JwtAuthenticationToken(jwt);
    }
}
