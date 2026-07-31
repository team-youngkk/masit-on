package com.masiton.security.infrastructure.redis;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Refresh tokens contain only cryptographically random bytes. Their owner lookup stays server-side.
 */
@Component
public class RefreshTokenFactory {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String create() {
        byte[] random = new byte[48];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
