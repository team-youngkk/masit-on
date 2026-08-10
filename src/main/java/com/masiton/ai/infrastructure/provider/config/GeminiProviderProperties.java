package com.masiton.ai.infrastructure.provider.config;

import java.time.Duration;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.ai.provider.gemini")
public class GeminiProviderProperties {

    public static final String MODEL_VERSION = "gemini-3-flash-preview";
    public static final String PROMPT_VERSION = "P1";
    public static final String SCHEMA_VERSION = "S1";

    private boolean enabled;
    private boolean freeTierVerified;
    private boolean paidBillingEnabled;
    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String model = MODEL_VERSION;
    private String promptVersion = PROMPT_VERSION;
    private String schemaVersion = SCHEMA_VERSION;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(90);

    @PostConstruct
    void validateFixedContract() {
        if (!MODEL_VERSION.equals(model) || !PROMPT_VERSION.equals(promptVersion) || !SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalStateException("Gemini model, prompt, and schema versions are fixed by the AI contract");
        }
        if (paidBillingEnabled) {
            throw new IllegalStateException("Gemini paid billing must remain disabled for Free Tier-only operation");
        }
        if (enabled && (apiKey.isBlank() || !freeTierVerified)) {
            throw new IllegalStateException("An enabled Gemini provider requires an API key and verified Free Tier status");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFreeTierVerified() { return freeTierVerified; }
    public void setFreeTierVerified(boolean freeTierVerified) { this.freeTierVerified = freeTierVerified; }
    public boolean isPaidBillingEnabled() { return paidBillingEnabled; }
    public void setPaidBillingEnabled(boolean paidBillingEnabled) { this.paidBillingEnabled = paidBillingEnabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
}
