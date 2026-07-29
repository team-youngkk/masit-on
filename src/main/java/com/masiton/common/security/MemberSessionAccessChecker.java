package com.masiton.common.security;

public interface MemberSessionAccessChecker {

    boolean isAllowed(String memberId, String sessionId);
}
