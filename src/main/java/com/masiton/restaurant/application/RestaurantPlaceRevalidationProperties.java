package com.masiton.restaurant.application;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.restaurant.place-revalidation")
public class RestaurantPlaceRevalidationProperties {
    private boolean enabled;
    private Duration pollInterval = Duration.ofMinutes(5);
    private Duration revalidationInterval = Duration.ofDays(1);
    private Duration leaseDuration = Duration.ofMinutes(5);
    private int batchSize = 20;
    private int maxAttempts = 5;
    private Duration firstBackoff = Duration.ofMinutes(1);
    private Duration maxBackoff = Duration.ofHours(1);

    @PostConstruct
    void validate() {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                || revalidationInterval == null || revalidationInterval.isNegative() || revalidationInterval.isZero()
                || leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || firstBackoff == null || firstBackoff.isNegative() || firstBackoff.isZero()
                || maxBackoff == null || maxBackoff.compareTo(firstBackoff) < 0
                || batchSize < 1 || batchSize > 100 || maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalStateException("Restaurant place revalidation bounds are invalid");
        }
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public Duration getRevalidationInterval() { return revalidationInterval; }
    public void setRevalidationInterval(Duration revalidationInterval) { this.revalidationInterval = revalidationInterval; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getFirstBackoff() { return firstBackoff; }
    public void setFirstBackoff(Duration firstBackoff) { this.firstBackoff = firstBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
}
