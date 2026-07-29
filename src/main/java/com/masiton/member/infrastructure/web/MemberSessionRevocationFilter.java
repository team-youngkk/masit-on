package com.masiton.member.infrastructure.web;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.security.infrastructure.web.SecurityErrorWriter;

@Component
public class MemberSessionRevocationFilter extends OncePerRequestFilter {
    private final MemberSessionRevocationStore revocations;
    private final SecurityErrorWriter errorWriter;

    public MemberSessionRevocationFilter(MemberSessionRevocationStore revocations, SecurityErrorWriter errorWriter) {
        this.revocations = revocations;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            String sessionId = authentication.getToken().getClaimAsString("sid");
            if (sessionId != null && isRevoked(sessionId)) {
                SecurityContextHolder.clearContext();
                errorWriter.commence(request, response, new InsufficientAuthenticationException("Member session has been revoked"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isRevoked(String sessionId) {
        try {
            return revocations.isRevoked(UUID.fromString(sessionId), Instant.now());
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }
}
