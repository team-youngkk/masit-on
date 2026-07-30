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
        return !(requestUri.equals("/api/me") || requestUri.startsWith("/api/me/")
                || (requestUri.equals("/api/auth/tokens") && HttpMethod.DELETE.matches(request.getMethod())));
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
}
