package com.masiton.security.presentation;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.security.application.AuthenticationResult;
import com.masiton.security.application.port.in.LoginAdminUseCase;
import com.masiton.security.application.port.in.LogoutAdminUseCase;
import com.masiton.security.application.port.in.RefreshAdminTokenUseCase;
import com.masiton.security.infrastructure.configuration.SecurityProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/auth/tokens")
public class AdminAuthenticationController {

    private final LoginAdminUseCase loginAdminUseCase;
    private final RefreshAdminTokenUseCase refreshAdminTokenUseCase;
    private final LogoutAdminUseCase logoutAdminUseCase;
    private final SecurityProperties properties;

    public AdminAuthenticationController(
            LoginAdminUseCase loginAdminUseCase,
            RefreshAdminTokenUseCase refreshAdminTokenUseCase,
            LogoutAdminUseCase logoutAdminUseCase,
            SecurityProperties properties
    ) {
        this.loginAdminUseCase = loginAdminUseCase;
        this.refreshAdminTokenUseCase = refreshAdminTokenUseCase;
        this.logoutAdminUseCase = logoutAdminUseCase;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(loginAdminUseCase.login(new LoginAdminUseCase.LoginCommand(request.loginId(), request.password())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(HttpServletRequest request) {
        String refreshToken = refreshToken(request);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return tokenResponse(refreshAdminTokenUseCase.refresh(refreshToken));
    }

    @DeleteMapping
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        String refreshToken = refreshToken(request);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        logoutAdminUseCase.logout(authentication.getName(), refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<AccessTokenResponse> tokenResponse(AuthenticationResult result) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new AccessTokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(properties.getPath())
                .maxAge(properties.getRefreshTokenTtl())
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(properties.getPath())
                .maxAge(Duration.ZERO)
                .build();
    }

    private String refreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(min = 12, max = 128) String password
    ) {
    }

    public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }
}
