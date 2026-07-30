package com.masiton.member.presentation;

import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;
import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.security.MemberCookieSettings;
import com.masiton.member.application.MemberAuthenticationResult;
import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.domain.model.MemberAccount;
import org.slf4j.MDC;

@RestController
@RequestMapping("/api/auth")
public class MemberAuthenticationController {
    private final MemberAuthenticationService service;
    private final MemberCookieSettings cookieSettings;

    public MemberAuthenticationController(MemberAuthenticationService service, MemberCookieSettings cookieSettings) {
        this.service = service;
        this.cookieSettings = cookieSettings;
    }

    @PostMapping("/registrations")
    public ResponseEntity<AcceptedResponse> register(@Valid @RequestBody CredentialsRequest request) {
        service.register(request.email(), request.password());
        return accepted();
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest request) {
        service.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verifications/resend")
    public ResponseEntity<AcceptedResponse> resendVerification(@Valid @RequestBody EmailRequest request) {
        service.resendVerification(request.email());
        return accepted();
    }

    @PostMapping("/password-resets/requests")
    public ResponseEntity<AcceptedResponse> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        service.requestPasswordReset(request.email());
        return accepted();
    }

    @PostMapping("/password-resets/confirmations")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tokens")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody CredentialsRequest request) {
        return tokenResponse(service.login(request.email(), request.password()));
    }

    @PostMapping("/tokens/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(HttpServletRequest request) {
        requireTrustedOrigin(request);
        return tokenResponse(service.refresh(requiredRefreshToken(request, true)));
    }

    @DeleteMapping("/tokens")
    public ResponseEntity<Void> logout(JwtAuthenticationToken authentication, HttpServletRequest request) {
        requireTrustedOrigin(request);
        service.logout(
                new MemberPrincipal(authentication.getName(), authentication.getToken().getClaimAsString("sid")),
                authentication.getToken().getExpiresAt(),
                requiredRefreshToken(request, false)
        );
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString()).build();
    }

    private ResponseEntity<AccessTokenResponse> tokenResponse(MemberAuthenticationResult result) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new AccessTokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
    }

    private ResponseEntity<AcceptedResponse> accepted() {
        return ResponseEntity.accepted().body(new AcceptedResponse(true));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
        boolean memberRefreshEndpoint = request.getRequestURI().equals("/api/auth/tokens/refresh")
                || request.getRequestURI().equals("/api/auth/tokens");
        if (memberRefreshEndpoint && (exception.code().equals("INVALID_REFRESH_TOKEN")
                || exception.code().equals("AUTHENTICATION_SERVICE_UNAVAILABLE"))) {
            response.header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());
        }
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return response.body(ErrorResponse.of(
                exception.code(), exception.getMessage(), exception.fieldErrors(),
                traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId));
    }

    private String requiredRefreshToken(HttpServletRequest request, boolean refreshRequest) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieSettings.cookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        if (refreshRequest) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
        }
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !origin.equals(cookieSettings.publicBaseUrl())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieSettings.cookieName(), value)
                .httpOnly(true).secure(cookieSettings.secure()).sameSite(cookieSettings.sameSite())
                .path(cookieSettings.path()).maxAge(cookieSettings.refreshTokenTtl()).build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(cookieSettings.cookieName(), "")
                .httpOnly(true).secure(cookieSettings.secure()).sameSite(cookieSettings.sameSite())
                .path(cookieSettings.path()).maxAge(Duration.ZERO).build();
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }
    public record CredentialsRequest(@NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 64) String password) { }
    public record TokenRequest(@NotBlank @Size(max = 200) String token) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 12, max = 64) String newPassword) { }
    public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) { }
    public record AcceptedResponse(boolean accepted) { }
}
