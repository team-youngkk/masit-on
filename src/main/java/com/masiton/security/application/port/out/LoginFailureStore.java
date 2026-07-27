package com.masiton.security.application.port.out;

public interface LoginFailureStore {

    boolean isBlocked(String loginId);

    void recordFailure(String loginId);

    void clear(String loginId);
}
