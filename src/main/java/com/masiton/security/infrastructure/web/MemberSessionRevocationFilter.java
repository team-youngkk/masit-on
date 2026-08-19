package com.masiton.security.infrastructure.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.masiton.common.security.MemberSessionAccessChecker;
import com.masiton.security.infrastructure.RestaurantPathClassifier;

@Component
public class MemberSessionRevocationFilter extends OncePerRequestFilter {
    private final MemberSessionAccessChecker sessionAccessChecker;
    private final SecurityErrorWriter errorWriter;

    public MemberSessionRevocationFilter(MemberSessionAccessChecker sessionAccessChecker, SecurityErrorWriter errorWriter) {
        this.sessionAccessChecker = sessionAccessChecker;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !(isProtectedRequest(requestUri, request.getMethod()) || isOptionalRestaurantDetailRequest(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            String sessionId = authentication.getToken().getClaimAsString("sid");
            MemberSessionAccessChecker.AccessDecision decision = sessionId == null
                    ? MemberSessionAccessChecker.AccessDecision.DENIED
                    : sessionAccessChecker.check(authentication.getName(), sessionId, currentRole(authentication));
            if (decision != MemberSessionAccessChecker.AccessDecision.ALLOWED) {
                SecurityContextHolder.clearContext();
                if (isOptionalRestaurantDetailRequest(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (decision == MemberSessionAccessChecker.AccessDecision.UNAVAILABLE) {
                    errorWriter.authenticationServiceUnavailable(request, response);
                    return;
                }
                errorWriter.commence(request, response, new InsufficientAuthenticationException("Member session is unavailable"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedRequest(String requestUri, String method) {
        return requestUri.equals("/api/me") || requestUri.startsWith("/api/me/")
                || requestUri.startsWith("/api/admin/")
                || (requestUri.equals("/api/auth/tokens") && HttpMethod.DELETE.matches(method));
    }

    private String currentRole(JwtAuthenticationToken authentication) {
        java.util.List<String> roles = authentication.getToken().getClaimAsStringList("roles");
        return roles != null && roles.size() == 1 ? roles.getFirst() : "__INVALID_ROLE__";
    }

    /**
     * API-POPULAR-001·API-MAP-001은 회원 문맥을 쓰지 않는 완전 공개 조회이므로 이 회원 세션 확인
     * 대상에서 제외한다. 제외하지 않으면 유효한 회원 JWT를 들고 온 요청마다 공개 조회에 불필요한
     * 세션 저장소 조회가 붙는다. 판정은 {@link RestaurantPathClassifier}가 보안 설정과 공유한다.
     */
    private boolean isOptionalRestaurantDetailRequest(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod())
                && RestaurantPathClassifier.isRestaurantDetailPath(request.getRequestURI());
    }
}
