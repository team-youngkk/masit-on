package com.masiton.security.infrastructure.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.VerificationCredentialVerifier;

@Component
public class BcryptVerificationCredentialVerifier implements VerificationCredentialVerifier {
    private final VerificationAccessProperties properties;
    private final PasswordEncoder passwordEncoder;

    public BcryptVerificationCredentialVerifier(VerificationAccessProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean matches(String loginId, String password) {
        String hash = properties.getPasswordHash();
        boolean idMatches = MessageDigest.isEqual(properties.getLoginId().getBytes(StandardCharsets.UTF_8),
                loginId.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = hash != null && hash.startsWith("$2") && hash.length() >= 59
                && passwordEncoder.matches(password, hash);
        return idMatches && passwordMatches;
    }
}
