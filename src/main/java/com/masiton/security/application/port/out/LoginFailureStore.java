package com.masiton.security.application.port.out;

public interface LoginFailureStore {

    boolean isBlocked(String loginId, String source);

    void recordFailure(String loginId, String source);

    void clear(String loginId, String source);
}
