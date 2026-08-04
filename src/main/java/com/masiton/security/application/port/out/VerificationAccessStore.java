package com.masiton.security.application.port.out;

import java.time.Duration;

public interface VerificationAccessStore {
    void save(String rawSessionId, Duration ttl);
    boolean exists(String rawSessionId);
    void delete(String rawSessionId);
    boolean isBlocked(String loginId, String source);
    void recordFailure(String loginId, String source);
    void clearFailures(String loginId, String source);
}
