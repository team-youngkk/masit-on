package com.masiton.common.idempotency.application;

import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;

public class InvalidIdempotencyKeyException extends BusinessException {

    public InvalidIdempotencyKeyException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "멱등성 키 형식을 확인해 주세요.");
    }
}
