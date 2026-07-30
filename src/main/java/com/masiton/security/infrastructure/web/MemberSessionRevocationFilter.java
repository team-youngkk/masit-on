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
        return !(isMemberProtectedRequest(requestUri, request.getMethod()) || isOptionalRestaurantDetailRequest(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            String sessionId = authentication.getToken().getClaimAsString("sid");
            MemberSessionAccessChecker.AccessDecision decision = sessionId == null
                    ? MemberSessionAccessChecker.AccessDecision.DENIED
                    : sessionAccessChecker.check(authentication.getName(), sessionId);
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

    private boolean isMemberProtectedRequest(String requestUri, String method) {
        return requestUri.equals("/api/me") || requestUri.startsWith("/api/me/")
                || (requestUri.equals("/api/auth/tokens") && HttpMethod.DELETE.matches(method));
    }

    private boolean isOptionalRestaurantDetailRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String detailPrefix = "/api/restaurants/";
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(detailPrefix)) {
            return false;
        }
        String restaurantId = requestUri.substring(detailPrefix.length());
        return !restaurantId.isEmpty() && !restaurantId.contains("/");
    }
}
