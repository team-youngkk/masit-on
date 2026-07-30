package com.masiton.member.infrastructure.configuration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Validated
@ConfigurationProperties("masiton.member.rate-limit")
public class MemberRateLimitProperties {

    @NotBlank
    private String secret;
    private String trustedProxyAddresses = "";
    private boolean reverseProxyEnabled;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Set<String> trustedProxyAddresses() {
        return Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void setTrustedProxyAddresses(String trustedProxyAddresses) {
        this.trustedProxyAddresses = trustedProxyAddresses;
    }

    public boolean isReverseProxyEnabled() {
        return reverseProxyEnabled;
    }

    public void setReverseProxyEnabled(boolean reverseProxyEnabled) {
        this.reverseProxyEnabled = reverseProxyEnabled;
    }

    @PostConstruct
    public void validateProxyBoundary() {
        if (reverseProxyEnabled && trustedProxyAddresses().isEmpty()) {
            throw new IllegalStateException("Trusted member proxy addresses are required when reverse proxy mode is enabled");
        }
    }
}
