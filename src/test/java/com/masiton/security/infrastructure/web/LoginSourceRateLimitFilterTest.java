package com.masiton.security.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.masiton.common.web.ClientAddressResolver;
import com.masiton.common.security.LoginSourceRateLimiter;

import static org.mockito.Mockito.mock;

class LoginSourceRateLimitFilterTest {

    private final LoginSourceRateLimiter rateLimits = mock(LoginSourceRateLimiter.class);
    private final ClientAddressResolver addresses = mock(ClientAddressResolver.class);
    private final SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final LoginSourceRateLimitFilter filter = new LoginSourceRateLimitFilter(rateLimits, addresses, errorWriter);

    @Test
    @DisplayName("형식이 잘못된 로그인도 MVC 전에 출처 제한을 적용한다")
    void 로그인_잘못된Json_출처제한을먼저적용한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/tokens");
        request.setContentType("application/json");
        request.setContent("{".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(addresses.resolve(request)).thenReturn("203.0.113.10");
        when(rateLimits.tryAcquireLoginSourceAttempt("203.0.113.10")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(rateLimits).tryAcquireLoginSourceAttempt("203.0.113.10");
        verify(errorWriter).invalidCredentials(any(), any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("로그인 이외 요청은 출처 제한과 본문 소비 없이 통과한다")
    void 로그인외요청_영향없음() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/tokens/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(rateLimits, never()).tryAcquireLoginSourceAttempt(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("출처 제한을 획득한 로그인 요청은 다음 필터로 진행한다")
    void 로그인_출처제한획득_필터체인진행() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/tokens");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(addresses.resolve(request)).thenReturn("203.0.113.10");
        when(rateLimits.tryAcquireLoginSourceAttempt("203.0.113.10")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(errorWriter, never()).invalidCredentials(any(), any());
        verify(errorWriter, never()).authenticationServiceUnavailable(any(), any());
    }

    @Test
    @DisplayName("출처 제한 저장소 장애는 503을 쓰고 다음 필터로 진행하지 않는다")
    void 로그인_출처제한저장소장애_503() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/tokens");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(addresses.resolve(request)).thenReturn("203.0.113.10");
        when(rateLimits.tryAcquireLoginSourceAttempt("203.0.113.10"))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        filter.doFilter(request, response, chain);

        verify(errorWriter).authenticationServiceUnavailable(request, response);
        verify(chain, never()).doFilter(any(), any());
    }
}
