package com.masiton.common.idempotency.application;

public class IdempotencyRecordAlreadyExistsException extends RuntimeException {

    public IdempotencyRecordAlreadyExistsException(Throwable cause) {
        super("Idempotency record already exists", cause);
    }
}
