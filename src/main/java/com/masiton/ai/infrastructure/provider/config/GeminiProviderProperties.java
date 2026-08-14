package com.masiton.ai.infrastructure.provider.config;

import java.net.URI;
import java.time.Duration;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.masiton.ai.application.AiExtractionContract;

@ConfigurationProperties("masiton.ai.provider.gemini")
public class GeminiProviderProperties {

    public static final String GLOBAL_ENDPOINT = "https://generativelanguage.googleapis.com";
    public static final String MODEL_VERSION = AiExtractionContract.MODEL_VERSION;
    public static final String PROMPT_VERSION = AiExtractionContract.PROMPT_VERSION;
    public static final String SCHEMA_VERSION = AiExtractionContract.SCHEMA_VERSION;
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(90);

    private boolean enabled;
    private boolean freeTierVerified;
    private boolean paidBillingEnabled;
    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String model = MODEL_VERSION;
    private String promptVersion = PROMPT_VERSION;
    private String schemaVersion = SCHEMA_VERSION;
    private Duration connectTimeout = CONNECT_TIMEOUT;
    private Duration responseTimeout = RESPONSE_TIMEOUT;

    @PostConstruct
    void validateFixedContract() {
        if (!hasFixedContract()) {
            throw new IllegalStateException("Gemini model, prompt, and schema versions are fixed by the AI contract");
        }
        if (paidBillingEnabled) {
            throw new IllegalStateException("Gemini paid billing must remain disabled for Free Tier-only operation");
        }
        if (enabled && (apiKey.isBlank() || !freeTierVerified)) {
            throw new IllegalStateException("An enabled Gemini provider requires an API key and verified Free Tier status");
        }
        if (enabled && (!hasUsableEndpoint() || !hasUsableTimeouts())) {
            throw new IllegalStateException("An enabled Gemini provider requires a valid endpoint and positive timeouts");
        }
    }

    boolean hasFixedContract() {
        return MODEL_VERSION.equals(model)
                && PROMPT_VERSION.equals(promptVersion)
                && SCHEMA_VERSION.equals(schemaVersion);
    }

    boolean hasUsableEndpoint() {
        return hasUsableEndpoint(false);
    }

    boolean hasUsableEndpoint(boolean allowLoopbackTestEndpoint) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            URI endpoint = URI.create(baseUrl.trim());
            boolean globalEndpoint = "https".equalsIgnoreCase(endpoint.getScheme())
                    && GLOBAL_ENDPOINT.equals("%s://%s".formatted(endpoint.getScheme(), endpoint.getHost()))
                    && endpoint.getPort() == -1;
            boolean loopbackTestEndpoint = allowLoopbackTestEndpoint
                    && "http".equalsIgnoreCase(endpoint.getScheme())
                    && ("localhost".equalsIgnoreCase(endpoint.getHost())
                    || "127.0.0.1".equals(endpoint.getHost()));
            boolean rootPath = endpoint.getPath() == null
                    || endpoint.getPath().isBlank()
                    || "/".equals(endpoint.getPath());
            return endpoint.isAbsolute()
                    && (globalEndpoint || loopbackTestEndpoint)
                    && endpoint.getHost() != null
                    && (globalEndpoint || endpoint.getPort() > 0)
                    && rootPath
                    && endpoint.getUserInfo() == null
                    && endpoint.getQuery() == null
                    && endpoint.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    boolean hasUsableTimeouts() {
        return hasUsableTimeouts(false);
    }

    boolean hasUsableTimeouts(boolean allowTestTimeouts) {
        if (allowTestTimeouts) {
            return connectTimeout != null && !connectTimeout.isZero() && !connectTimeout.isNegative()
                    && responseTimeout != null && !responseTimeout.isZero() && !responseTimeout.isNegative();
        }
        return CONNECT_TIMEOUT.equals(connectTimeout) && RESPONSE_TIMEOUT.equals(responseTimeout);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFreeTierVerified() { return freeTierVerified; }
    public void setFreeTierVerified(boolean freeTierVerified) { this.freeTierVerified = freeTierVerified; }
    public boolean isPaidBillingEnabled() { return paidBillingEnabled; }
    public void setPaidBillingEnabled(boolean paidBillingEnabled) { this.paidBillingEnabled = paidBillingEnabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.trim(); }
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
