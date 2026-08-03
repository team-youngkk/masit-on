package com.masiton.common.web;

import java.util.List;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ErrorResponse.FieldError> fieldErrors;
    private final Long retryAfterSeconds;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.status(), errorCode.name(), errorCode.defaultMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode.status(), errorCode.name(), message, List.of());
    }

    /**
     * errors[].field에 검증 실패 필드를, errors[].reason에 안전한 실패 사유를 담는다.
     * 최상위 message는 ErrorCode의 일반화된 기본 메시지를 그대로 사용한다.
     */
    public BusinessException(ErrorCode errorCode, String field, String reason) {
        this(errorCode.status(), errorCode.name(), errorCode.defaultMessage(),
                List.of(new ErrorResponse.FieldError(field, reason)));
    }

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    /** retryAfterSeconds가 있으면 응답에 Retry-After 헤더(초 단위)를 추가한다. */
    public BusinessException(HttpStatus status, String code, String message, long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.of();
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public BusinessException(
            HttpStatus status, String code, String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
        this.retryAfterSeconds = null;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ErrorResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
