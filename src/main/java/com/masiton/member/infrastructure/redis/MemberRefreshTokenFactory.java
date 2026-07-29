package com.masiton.member.infrastructure.redis;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
class MemberRefreshTokenFactory {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    String create() {
        byte[] random = new byte[48];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
