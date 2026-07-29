package com.masiton.member.presentation;

import java.time.Duration;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.member.application.MemberAuthenticationResult;
import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.security.infrastructure.configuration.SecurityProperties;

@RestController
@RequestMapping("/api/auth")
public class MemberAuthenticationController {
    private final MemberAuthenticationService service;
    private final SecurityProperties properties;

    public MemberAuthenticationController(MemberAuthenticationService service, SecurityProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/registrations")
    public ResponseEntity<Void> register(@Valid @RequestBody CredentialsRequest request) {
        service.register(request.email(), request.password());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest request) {
        service.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verifications/resend")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        service.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets/requests")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        service.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets/confirmations")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tokens")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody CredentialsRequest request) {
        return tokenResponse(service.login(request.email(), request.password()));
    }

    @PostMapping("/tokens/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(HttpServletRequest request) {
        return tokenResponse(service.refresh(requiredRefreshToken(request)));
    }

    @DeleteMapping("/tokens")
    public ResponseEntity<Void> logout(JwtAuthenticationToken authentication, HttpServletRequest request) {
        service.logout(authentication.getName(), requiredRefreshToken(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString()).build();
    }

    private ResponseEntity<AccessTokenResponse> tokenResponse(MemberAuthenticationResult result) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new AccessTokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
    }

    private String requiredRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (properties.getMember().getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(properties.getMember().getCookieName(), value)
                .httpOnly(true).secure(properties.isSecure()).sameSite(properties.getSameSite())
                .path(properties.getMember().getPath()).maxAge(properties.getMember().getRefreshTokenTtl()).build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(properties.getMember().getCookieName(), "")
                .httpOnly(true).secure(properties.isSecure()).sameSite(properties.getSameSite())
                .path(properties.getMember().getPath()).maxAge(Duration.ZERO).build();
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }
    public record CredentialsRequest(@NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 128) String password) { }
    public record TokenRequest(@NotBlank @Size(max = 200) String token) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 12, max = 128) String password) { }
    public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) { }
}
