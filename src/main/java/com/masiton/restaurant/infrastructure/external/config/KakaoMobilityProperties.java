package com.masiton.restaurant.infrastructure.external.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kakao Mobility 자동차 길찾기 호출 설정이다. ADR-ROUTE-001 5.2절·9절과 NFR-EXTERNAL-005,
 * NFR-COST-001의 timeout·quota·유료 호출 금지 규칙을 기동 시점에 강제한다.
 */
@ConfigurationProperties("masiton.integration.kakao-mobility")
public class KakaoMobilityProperties {

    private boolean enabled;
    private boolean freeTierVerified;
    private boolean paidBillingEnabled;
    private String restApiKey = "";
    private String baseUrl = "https://apis-navi.kakaomobility.com";
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration responseTimeout = Duration.ofSeconds(4);
    private Duration totalTimeout = Duration.ofSeconds(5);
    private int monthlyQuota = 1_000;
    private int requestsPerSecond = 20;
    private int maxConcurrentRequests = 20;
    private boolean localBaseUrlAllowed;

    @PostConstruct
    void validateFixedContract() {
        if (paidBillingEnabled) {
            throw new IllegalStateException("Kakao Mobility paid billing must remain disabled for Free Tier-only operation");
        }
        if (enabled && (restApiKey.isBlank() || !freeTierVerified)) {
            throw new IllegalStateException("An enabled Kakao Mobility provider requires an API key and verified Free Tier status");
        }
        if (monthlyQuota < 1 || monthlyQuota > 1_000) {
            throw new IllegalStateException("Kakao Mobility monthly quota must be between 1 and 1000");
        }
        if (requestsPerSecond < 1 || maxConcurrentRequests < 1) {
            throw new IllegalStateException("Kakao Mobility rate and concurrency limits must be positive");
        }
        validateBaseUrl();
        if (connectTimeout == null || responseTimeout == null || totalTimeout == null
                || connectTimeout.isZero() || connectTimeout.isNegative()
                || responseTimeout.isZero() || responseTimeout.isNegative()
                || totalTimeout.isZero() || totalTimeout.isNegative()) {
            throw new IllegalStateException("Kakao Mobility connect, response, and total timeouts must be positive");
        }
        if (connectTimeout.plus(responseTimeout).compareTo(totalTimeout) > 0) {
            throw new IllegalStateException("Kakao Mobility connect and response timeouts must fit within the total timeout budget");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFreeTierVerified() { return freeTierVerified; }
    public void setFreeTierVerified(boolean freeTierVerified) { this.freeTierVerified = freeTierVerified; }
    public boolean isPaidBillingEnabled() { return paidBillingEnabled; }
    public void setPaidBillingEnabled(boolean paidBillingEnabled) { this.paidBillingEnabled = paidBillingEnabled; }
    public String getRestApiKey() { return restApiKey; }
    public void setRestApiKey(String restApiKey) { this.restApiKey = restApiKey == null ? "" : restApiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
    public Duration getTotalTimeout() { return totalTimeout; }
    public void setTotalTimeout(Duration totalTimeout) { this.totalTimeout = totalTimeout; }
    public int getMonthlyQuota() { return monthlyQuota; }
    public void setMonthlyQuota(int monthlyQuota) { this.monthlyQuota = monthlyQuota; }
    public int getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    public boolean isLocalBaseUrlAllowed() { return localBaseUrlAllowed; }
    public void setLocalBaseUrlAllowed(boolean localBaseUrlAllowed) { this.localBaseUrlAllowed = localBaseUrlAllowed; }

    private void validateBaseUrl() {
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getHost() == null) {
                throw new IllegalStateException("Kakao Mobility base URL must not contain credentials or query data");
            }
            boolean productionHost = "https".equalsIgnoreCase(uri.getScheme())
                    && "apis-navi.kakaomobility.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1;
            boolean localHost = localBaseUrlAllowed
                    && "http".equalsIgnoreCase(uri.getScheme())
                    && uri.getPort() > 0
                    && Set.of("localhost", "127.0.0.1", "::1").contains(uri.getHost());
            if (enabled && !productionHost && !localHost) {
                throw new IllegalStateException("Kakao Mobility base URL is not an allowed provider endpoint");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Kakao Mobility base URL is invalid", exception);
        }
    }
}
