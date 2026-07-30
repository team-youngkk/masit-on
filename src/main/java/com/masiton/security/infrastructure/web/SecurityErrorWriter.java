package com.masiton.security.infrastructure.web;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.security.MemberCookieSettings;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    private final MemberCookieSettings memberCookieSettings;

    public SecurityErrorWriter(ObjectMapper objectMapper, MemberCookieSettings memberCookieSettings) {
        this.objectMapper = objectMapper;
        this.memberCookieSettings = memberCookieSettings;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        write(request, response, ErrorCode.FORBIDDEN);
    }

    public void authenticationServiceUnavailable(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(503);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        setCacheControl(request, response);
        if (isMemberLogout(request)) {
            response.setHeader(HttpHeaders.SET_COOKIE, expiredMemberRefreshCookie().toString());
        }
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                "AUTHENTICATION_SERVICE_UNAVAILABLE",
                "인증 서비스를 일시적으로 사용할 수 없습니다.",
                traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId
        ));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        setCacheControl(request, response);
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                errorCode.name(),
                errorCode.defaultMessage(),
                traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId
        ));
    }

    private void setCacheControl(HttpServletRequest request, HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        boolean memberPrivatePath = requestUri.equals("/api/me") || requestUri.startsWith("/api/me/");
        response.setHeader("Cache-Control", memberPrivatePath ? "private, no-store" : "no-store");
    }

    private boolean isMemberLogout(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/auth/tokens")
                && "DELETE".equals(request.getMethod());
    }

    private ResponseCookie expiredMemberRefreshCookie() {
        return ResponseCookie.from(memberCookieSettings.cookieName(), "")
                .httpOnly(true)
                .secure(memberCookieSettings.secure())
                .sameSite(memberCookieSettings.sameSite())
                .path(memberCookieSettings.path())
                .maxAge(Duration.ZERO)
                .build();
    }
}
