package com.masiton.security.presentation;

import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorResponse;
import com.masiton.security.application.VerificationSessionService;
import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;
import com.masiton.security.infrastructure.web.VerificationClientAddressResolver;

@RestController
public class VerificationSessionController {

    private final VerificationSessionService service;
    private final VerificationAccessProperties properties;
    private final VerificationClientAddressResolver clientAddressResolver;

    public VerificationSessionController(VerificationSessionService service, VerificationAccessProperties properties,
            VerificationClientAddressResolver clientAddressResolver) {
        this.service = service;
        this.properties = properties;
        this.clientAddressResolver = clientAddressResolver;
    }

    @PostMapping("/api/verification/sessions")
    public ResponseEntity<Void> create(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        requireSameOrigin(request);
        String rawSessionId = service.create(body.loginId(), body.password(), clientAddressResolver.resolve(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessionCookie(rawSessionId).toString()).build();
    }

    @DeleteMapping("/api/verification/sessions")
    public ResponseEntity<Void> delete(HttpServletRequest request) {
        requireSameOrigin(request);
        service.revoke(sessionId(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredCookie().toString()).build();
    }

    @GetMapping("/internal/verification/session")
    public ResponseEntity<Void> validate(HttpServletRequest request) {
        return service.isValid(sessionId(request)) ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/internal/verification/access-required")
    public ResponseEntity<ErrorResponse> accessRequired() {
        return error(HttpStatus.UNAUTHORIZED, "VALIDATION_ACCESS_REQUIRED", "검증 참여자 로그인이 필요합니다.");
    }

    @GetMapping("/internal/verification/unavailable")
    public ResponseEntity<ErrorResponse> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "VALIDATION_SESSION_UNAVAILABLE", "검증 세션을 확인할 수 없습니다.");
    }

    private void requireSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!properties.getPublicBaseUrl().equals(origin)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
        }
    }

    private ResponseCookie sessionCookie(String value) {
        return ResponseCookie.from(properties.getCookieName(), value).httpOnly(true).secure(true)
                .sameSite("Strict").path("/").maxAge(properties.getSessionTtl()).build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(properties.getCookieName(), "").httpOnly(true).secure(true)
                .sameSite("Strict").path("/").maxAge(Duration.ZERO).build();
    }

    private String sessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (properties.getCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        if (traceId == null) traceId = UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, traceId));
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(max = 128) String password) { }
}
