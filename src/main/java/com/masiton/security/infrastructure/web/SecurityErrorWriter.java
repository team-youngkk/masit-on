package com.masiton.security.infrastructure.web;

import java.io.IOException;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        response.setHeader("Cache-Control", "no-store");
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
        response.setHeader("Cache-Control", "no-store");
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                errorCode.name(),
                errorCode.defaultMessage(),
                traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId
        ));
    }
}
