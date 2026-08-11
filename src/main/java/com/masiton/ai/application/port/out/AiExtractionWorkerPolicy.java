package com.masiton.ai.application.port.out;

import java.time.Duration;

public interface AiExtractionWorkerPolicy {
    boolean isEnabled();
    Duration getLeaseDuration();
    Duration getHeartbeatInterval();
    Duration getDrainTimeout();
    Duration getQuotaWindow();
    int getMaxAttempts();
    Duration getFirstBackoff();
    Duration getSecondBackoff();
    long getApplicationQuotaLimit();
    int getQuotaWarningPercent();
}
