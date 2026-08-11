package com.masiton.ai.infrastructure.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.ai.temporary-input")
public class TemporaryInputEncryptionProperties {
    private String activeKeyId = "";
    private String activeKey = "";
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId == null ? "" : activeKeyId; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey == null ? "" : activeKey; }

    public Map<String, String> getKeys() {
        Map<String, String> configuredKeys = new LinkedHashMap<>(keys);
        if (!activeKeyId.isBlank() && !activeKey.isBlank()) {
            configuredKeys.put(activeKeyId, activeKey);
        }
        return Map.copyOf(configuredKeys);
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }
}
