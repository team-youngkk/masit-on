package com.masiton.security.application;

/**
 * Raised by the refresh-token adapter after an absent, stale, or replayed token is rejected.
 */
public class InvalidRefreshTokenException extends RuntimeException {
}
