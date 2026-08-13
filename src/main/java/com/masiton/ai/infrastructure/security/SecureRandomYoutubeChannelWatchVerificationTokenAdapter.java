package com.masiton.ai.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.masiton.ai.application.port.out.YoutubeChannelWatchVerificationTokenPort;

@Component
public class SecureRandomYoutubeChannelWatchVerificationTokenAdapter
        implements YoutubeChannelWatchVerificationTokenPort {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String issue(String channelId) {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
