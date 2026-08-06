package com.masiton.common.idempotency.application;

import java.util.Objects;

public record IdempotencyExecutionResult(
        IdempotencyResponse response,
        boolean replayed
) {

    public IdempotencyExecutionResult {
        Objects.requireNonNull(response, "response must not be null");
    }

    public static IdempotencyExecutionResult created(IdempotencyResponse response) {
        return new IdempotencyExecutionResult(response, false);
    }

    public static IdempotencyExecutionResult replayed(IdempotencyResponse response) {
        return new IdempotencyExecutionResult(response, true);
    }
}
