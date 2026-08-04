package com.masiton.common.idempotency.application;

import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;

public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "이미 다른 요청에 사용된 멱등성 키입니다.");
    }
}
