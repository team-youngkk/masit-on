package com.masiton.ai.infrastructure.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.ai.temporary-input")
public class TemporaryInputEncryptionProperties {
    private String activeKeyId = "";
    private String activeKey = "";

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId == null ? "" : activeKeyId; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey == null ? "" : activeKey; }
}
