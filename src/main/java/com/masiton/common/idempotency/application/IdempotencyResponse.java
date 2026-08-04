package com.masiton.common.idempotency.application;

import java.util.Objects;
import java.util.UUID;

public record IdempotencyResponse(
        int status,
        String body,
        UUID resourceId
) {

    public IdempotencyResponse {
        if (status != 201) {
            throw new IllegalArgumentException("Idempotent creation response status must be 201");
        }
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
    }
}
