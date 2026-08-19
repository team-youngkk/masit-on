package com.masiton.common.security;

/** Security 경계가 로그인 요청의 source-only 제한을 선행 획득하기 위한 공통 계약이다. */
public interface LoginSourceRateLimiter {
    boolean tryAcquireLoginSourceAttempt(String source);
}
