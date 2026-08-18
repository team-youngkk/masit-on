package com.masiton.security.infrastructure.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.member.infrastructure.web.MemberClientAddressResolver;

/** Runs before MVC consumes JSON so malformed login requests cannot bypass the source quota. */
@Component
public class LoginSourceRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/auth/tokens";

    private final MemberRateLimitStore rateLimits;
    private final MemberClientAddressResolver clientAddressResolver;
    private final SecurityErrorWriter errorWriter;

    public LoginSourceRateLimitFilter(MemberRateLimitStore rateLimits, MemberClientAddressResolver clientAddressResolver,
            SecurityErrorWriter errorWriter) {
        this.rateLimits = rateLimits;
        this.clientAddressResolver = clientAddressResolver;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LOGIN_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (!rateLimits.tryAcquireLoginSourceAttempt(clientAddressResolver.resolve(request))) {
                errorWriter.invalidCredentials(request, response);
                return;
            }
        } catch (RuntimeException exception) {
            errorWriter.authenticationServiceUnavailable(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
