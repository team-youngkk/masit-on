package com.masiton.security.application;

import java.time.OffsetDateTime;

/** Raw token is returned only to the preview response and must not be persisted or logged. */
public record IssuedConfirmationToken(String rawToken, OffsetDateTime expiresAt) {
}
