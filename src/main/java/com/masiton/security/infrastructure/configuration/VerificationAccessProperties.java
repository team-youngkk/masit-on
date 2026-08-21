package com.masiton.security.infrastructure.configuration;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.VerificationSessionSettings;

@Component
@ConfigurationProperties("masiton.security.verification")
public class VerificationAccessProperties implements VerificationSessionSettings {

    private boolean enabled = true;
    private String loginId = "";
    private String passwordHash = "";
    private String publicBaseUrl = "http://localhost:3000";
    private String cookieName = "__Host-masiton-verification";
    private Duration sessionTtl = Duration.ofDays(7);
    private Duration failureTtl = Duration.ofMinutes(15);
    private int maxAttempts = 5;
    private String trustedProxyAddresses = "";
    private boolean reverseProxyEnabled;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getCookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
    public Duration getFailureTtl() { return failureTtl; }
    public void setFailureTtl(Duration failureTtl) { this.failureTtl = failureTtl; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Set<String> trustedProxyAddresses() {
        return Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
    public void setTrustedProxyAddresses(String trustedProxyAddresses) {
        this.trustedProxyAddresses = trustedProxyAddresses;
    }
    public boolean isReverseProxyEnabled() { return reverseProxyEnabled; }
    public void setReverseProxyEnabled(boolean reverseProxyEnabled) { this.reverseProxyEnabled = reverseProxyEnabled; }

    @PostConstruct
    public void validateProxyBoundary() {
        if (reverseProxyEnabled && trustedProxyAddresses().isEmpty()) {
            throw new IllegalStateException(
                    "Trusted verification proxy addresses are required when reverse proxy mode is enabled");
        }
    }

    @Override public Duration sessionTtl() { return sessionTtl; }
    @Override public Duration failureTtl() { return failureTtl; }
}
