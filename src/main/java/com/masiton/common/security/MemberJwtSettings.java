package com.masiton.common.security;

import java.time.Duration;

/** Neutral settings contract shared by the member token adapter and JWT configuration. */
public record MemberJwtSettings(String issuer, String audience, Duration accessTokenTtl, String keyId) {
}
