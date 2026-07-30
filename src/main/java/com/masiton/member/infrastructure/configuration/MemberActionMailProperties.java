package com.masiton.member.infrastructure.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.member.action-mail")
public class MemberActionMailProperties {

    private String activeKeyId;
    private String activeKey;
    private Map<String, String> keys = new LinkedHashMap<>();

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
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("An active member action-mail encryption key id is required");
        }
        String configuredActiveKey = getKeys().get(activeKeyId);
        if (configuredActiveKey == null || configuredActiveKey.isBlank()) {
            throw new IllegalStateException("The active member action-mail encryption key must be configured");
        }
    }
}
