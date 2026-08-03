package com.masiton.security.application.port.out;

public interface VerificationCredentialVerifier {
    boolean matches(String loginId, String password);
}
