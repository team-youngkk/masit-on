package com.masiton.security.infrastructure.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.VerificationCredentialVerifier;

@Component
public class BcryptVerificationCredentialVerifier implements VerificationCredentialVerifier {
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi.0P8EIw1PhqcoUL24TJnS0W9TuP.2";

    private final VerificationAccessProperties properties;
    private final PasswordEncoder passwordEncoder;

    public BcryptVerificationCredentialVerifier(VerificationAccessProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean matches(String loginId, String password) {
        String hash = properties.getPasswordHash();
        boolean configuredHash = hash != null && hash.startsWith("$2") && hash.length() >= 59;
        boolean idMatches = MessageDigest.isEqual(properties.getLoginId().getBytes(StandardCharsets.UTF_8),
                loginId.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = passwordEncoder.matches(password, configuredHash ? hash : DUMMY_PASSWORD_HASH);
        return properties.isEnabled() && configuredHash && idMatches && passwordMatches;
    }
}
