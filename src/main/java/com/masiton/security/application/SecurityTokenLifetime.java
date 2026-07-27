package com.masiton.security.application;

import java.time.Duration;

/**
 * Keeps the application independent of configuration binding and its infrastructure package.
 */
public record SecurityTokenLifetime(Duration accessTokenTtl, Duration refreshTokenTtl) {
}
