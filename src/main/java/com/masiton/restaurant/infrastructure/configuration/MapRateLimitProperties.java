package com.masiton.restaurant.infrastructure.configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("masiton.restaurant.map.rate-limit")
public class MapRateLimitProperties {

    private String trustedProxyAddresses = "";
    private boolean reverseProxyEnabled;

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
            throw new IllegalStateException("Trusted map proxy addresses are required when reverse proxy mode is enabled");
        }
    }
}
