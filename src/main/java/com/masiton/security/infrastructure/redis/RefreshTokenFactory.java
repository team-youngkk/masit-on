package com.masiton.security.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Prefixing an opaque random value with a base64url-encoded internal id lets Redis address the
 * per-admin key without an index or a key scan. The id is always verified against the stored hash.
 */
@Component
public class RefreshTokenFactory {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String create(String adminId) {
        byte[] random = new byte[48];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(adminId.getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public Optional<String> extractAdminId(String token) {
        if (token == null || token.length() > 1024) {
            return Optional.empty();
        }
        int separator = token.indexOf('.');
        if (separator < 1 || separator != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        try {
            String adminId = new String(Base64.getUrlDecoder().decode(token.substring(0, separator)), StandardCharsets.UTF_8);
            return adminId.isBlank() || adminId.length() > 100 ? Optional.empty() : Optional.of(adminId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
