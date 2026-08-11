package com.masiton.ai.infrastructure.worker;

import java.time.Duration;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.masiton.ai.application.port.out.AiExtractionWorkerPolicy;

@ConfigurationProperties("masiton.ai.worker")
public class AiExtractionWorkerProperties implements AiExtractionWorkerPolicy {

    private boolean enabled;
    private Duration pollInterval = Duration.ofSeconds(5);
    private Duration leaseDuration = Duration.ofSeconds(120);
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private Duration drainTimeout = Duration.ofSeconds(30);
    private Duration quotaWindow = Duration.ofDays(1);
    private int maxAttempts = 3;
    private Duration firstBackoff = Duration.ofSeconds(5);
    private Duration secondBackoff = Duration.ofSeconds(30);
    private long providerQuotaLimit;
    private long applicationQuotaLimit;
    private int quotaWarningPercent = 80;

    @PostConstruct
    void validate() {
        if (!Duration.ofSeconds(5).equals(pollInterval)
                || !Duration.ofSeconds(120).equals(leaseDuration)
                || !Duration.ofSeconds(30).equals(heartbeatInterval)
                || !Duration.ofSeconds(30).equals(drainTimeout)
                || maxAttempts != 3
                || !Duration.ofSeconds(5).equals(firstBackoff)
                || !Duration.ofSeconds(30).equals(secondBackoff)) {
            throw new IllegalStateException("AI worker timing and retry policy is fixed by ADR-EXT-003");
        }
        if (enabled && (quotaWindow == null || quotaWindow.isZero() || quotaWindow.isNegative()
                || providerQuotaLimit <= 0 || applicationQuotaLimit <= 0
                || applicationQuotaLimit >= providerQuotaLimit
                || quotaWarningPercent != 80)) {
            throw new IllegalStateException("Enabled AI worker requires a verified fail-closed Free Tier quota");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    public Duration getDrainTimeout() { return drainTimeout; }
    public void setDrainTimeout(Duration drainTimeout) { this.drainTimeout = drainTimeout; }
    public Duration getQuotaWindow() { return quotaWindow; }
    public void setQuotaWindow(Duration quotaWindow) { this.quotaWindow = quotaWindow; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getFirstBackoff() { return firstBackoff; }
    public void setFirstBackoff(Duration firstBackoff) { this.firstBackoff = firstBackoff; }
    public Duration getSecondBackoff() { return secondBackoff; }
    public void setSecondBackoff(Duration secondBackoff) { this.secondBackoff = secondBackoff; }
    public long getProviderQuotaLimit() { return providerQuotaLimit; }
    public void setProviderQuotaLimit(long providerQuotaLimit) { this.providerQuotaLimit = providerQuotaLimit; }
    public long getApplicationQuotaLimit() { return applicationQuotaLimit; }
    public void setApplicationQuotaLimit(long applicationQuotaLimit) { this.applicationQuotaLimit = applicationQuotaLimit; }
    public int getQuotaWarningPercent() { return quotaWarningPercent; }
    public void setQuotaWarningPercent(int quotaWarningPercent) { this.quotaWarningPercent = quotaWarningPercent; }
}
