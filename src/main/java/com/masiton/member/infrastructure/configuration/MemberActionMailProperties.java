package com.masiton.member.infrastructure.configuration;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@ConfigurationProperties("masiton.member.action-mail")
public class MemberActionMailProperties {

    private final Environment environment;
    private String activeKeyId;
    private String activeKey;
    private String fromAddress;
    private String passwordResetUrl = "http://localhost:3000/password-reset";
    private Map<String, String> keys = new LinkedHashMap<>();

    public MemberActionMailProperties() {
        this(null);
    }

    @Autowired
    public MemberActionMailProperties(Environment environment) {
        this.environment = environment;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getPasswordResetUrl() {
        return passwordResetUrl;
    }

    public void setPasswordResetUrl(String passwordResetUrl) {
        this.passwordResetUrl = passwordResetUrl;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    public Map<String, String> getKeys() {
        Map<String, String> configuredKeys = new LinkedHashMap<>(keys);
        if (activeKey != null && !activeKey.isBlank()) {
            configuredKeys.putIfAbsent(activeKeyId, activeKey);
        }
        return Map.copyOf(configuredKeys);
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = new LinkedHashMap<>(keys);
    }

    @PostConstruct
    public void validate() {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("A member action-mail from address is required");
        }
        if (!isValidPasswordResetUrl(passwordResetUrl)) {
            throw new IllegalStateException("A valid member password-reset URL is required");
        }
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("An active member action-mail encryption key id is required");
        }
        String configuredActiveKey = getKeys().get(activeKeyId);
        if (configuredActiveKey == null || configuredActiveKey.isBlank()) {
            throw new IllegalStateException("The active member action-mail encryption key must be configured");
        }
    }

    private boolean isValidPasswordResetUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean http = "http".equalsIgnoreCase(uri.getScheme());
            return (https || (http && !isProductionProfile() && isLoopbackHost(uri.getHost())))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getRawFragment() == null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isProductionProfile() {
        return environment != null && environment.acceptsProfiles(Profiles.of("prod"));
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host);
    }
}
