package com.masiton.ai.infrastructure.worker;

import java.time.Duration;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillPolicy;
import com.masiton.common.web.OriginCanonicalizer;

@ConfigurationProperties("masiton.ai.youtube-backfill")
public class YoutubeChannelBackfillProperties implements YoutubeChannelBackfillPolicy {
    private boolean enabled;
    private int runIntervalSeconds;
    private Duration pollInterval = Duration.ofSeconds(30);
    private Duration leaseDuration = Duration.ofMinutes(2);
    private String baseUrl = "https://www.googleapis.com";
    private List<String> allowedOrigins = List.of("https://www.googleapis.com");
    private String apiKey = "";
    private Duration quotaWindow = Duration.ofDays(1);
    private long providerQuotaLimit;
    private long backfillQuotaLimit;
    private int maxPagesPerRun = 10;
    private int maxVideosPerRun = 500;

    @PostConstruct
    void validate() {
        if (runIntervalSeconds < 0 || (enabled && runIntervalSeconds == 0)) {
            throw new IllegalStateException("Enabled YouTube backfill requires an explicit positive run interval");
        }
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("YouTube backfill origin allow-list is required");
        }
        try {
            String baseOrigin = OriginCanonicalizer.canonicalize(baseUrl);
            if (allowedOrigins.stream().map(OriginCanonicalizer::canonicalize).noneMatch(baseOrigin::equals)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) { throw new IllegalStateException("YouTube backfill origin is not allowed", exception); }
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                || leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || quotaWindow == null || quotaWindow.isNegative() || quotaWindow.isZero()
                || maxPagesPerRun <= 0 || maxVideosPerRun <= 0
                || maxPagesPerRun > 1_000 || maxVideosPerRun > 50_000) {
            throw new IllegalStateException("YouTube backfill timing and bounds must be positive");
        }
        if (enabled && (providerQuotaLimit <= 0 || backfillQuotaLimit <= 0
                || backfillQuotaLimit > providerQuotaLimit)) {
            throw new IllegalStateException("Enabled YouTube backfill requires verified quota limits");
        }
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("Enabled YouTube backfill requires an API key");
        }
        if (enabled && (baseUrl == null || !baseUrl.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://"))) {
            throw new IllegalStateException("Enabled YouTube backfill requires an HTTPS base URL");
        }
    }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean value) { enabled = value; }
    public int getRunIntervalSeconds() { return runIntervalSeconds; }
    public void setRunIntervalSeconds(int value) { runIntervalSeconds = value; }
    public Duration getPollInterval() { return pollInterval; } public void setPollInterval(Duration value) { pollInterval = value; }
    public Duration getLeaseDuration() { return leaseDuration; } public void setLeaseDuration(Duration value) { leaseDuration = value; }
    public String getBaseUrl() { return baseUrl; } public void setBaseUrl(String value) { baseUrl = value; }
    public List<String> getAllowedOrigins() { return allowedOrigins; } public void setAllowedOrigins(List<String> value) { allowedOrigins = value; }
    public String getApiKey() { return apiKey; } public void setApiKey(String value) { apiKey = value; }
    public Duration getQuotaWindow() { return quotaWindow; }
    public void setQuotaWindow(Duration value) { quotaWindow = value; }
    public long getProviderQuotaLimit() { return providerQuotaLimit; }
    public void setProviderQuotaLimit(long value) { providerQuotaLimit = value; }
    public long getBackfillQuotaLimit() { return backfillQuotaLimit; }
    public void setBackfillQuotaLimit(long value) { backfillQuotaLimit = value; }
    public int getMaxPagesPerRun() { return maxPagesPerRun; }
    public void setMaxPagesPerRun(int value) { maxPagesPerRun = value; }
    public int getMaxVideosPerRun() { return maxVideosPerRun; }
    public void setMaxVideosPerRun(int value) { maxVideosPerRun = value; }
}
