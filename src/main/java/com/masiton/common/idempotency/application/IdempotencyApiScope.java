package com.masiton.common.idempotency.application;

import java.util.Arrays;

public enum IdempotencyApiScope {
    MEMBER_COLLECTIONS("POST:/api/me/collections"),
    ADMIN_CURATIONS("POST:/api/admin/curations"),
    MEMBER_SUBMISSIONS("POST:/api/me/submissions"),
    MEMBER_REPORTS("POST:/api/me/reports");

    private final String value;

    IdempotencyApiScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static IdempotencyApiScope fromValue(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown idempotency API scope"));
    }
}
