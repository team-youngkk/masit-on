package com.masiton.security.infrastructure.web;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.masiton.security.infrastructure.configuration.SecurityProperties;
import com.masiton.common.web.TrustedOriginResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminCookieOriginFilter extends OncePerRequestFilter {

    private static final String TOKEN_PATH = "/api/admin/auth/tokens";
    private static final String REFRESH_PATH = TOKEN_PATH + "/refresh";

    private final SecurityProperties properties;
    private final SecurityErrorWriter errorWriter;

    public AdminCookieOriginFilter(SecurityProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !(HttpMethod.POST.matches(request.getMethod()) && REFRESH_PATH.equals(requestUri))
                && !(HttpMethod.DELETE.matches(request.getMethod()) && TOKEN_PATH.equals(requestUri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (TrustedOriginResolver.resolveSingleOrigin(request)
                .filter(properties::isAllowedPublicOrigin)
                .isEmpty()) {
            reject(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errorWriter.handle(request, response, new AccessDeniedException("Untrusted admin cookie origin"));
    }
}
