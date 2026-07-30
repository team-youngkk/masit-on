package com.masiton.common.web;

import org.springframework.http.HttpStatus;

/**
 * 기능별 코드(`*_NOT_FOUND`, `DUPLICATE_*`)는 각 Workstream이 소유하므로 여기에 두지 않는다.
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식을 확인해 주세요."),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "필수 입력값이 누락되었습니다."),
    INVALID_FIELD_VALUE(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    INVALID_IDENTIFIER(HttpStatus.BAD_REQUEST, "식별자 형식이 올바르지 않습니다."),
    INVALID_CONFIRMATION_TOKEN(HttpStatus.BAD_REQUEST, "확인 토큰이 유효하지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    AUTHENTICATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 상태를 확인할 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다."),
    IDENTITY_VERIFICATION_REQUIRED(HttpStatus.CONFLICT, "동일 자원 여부를 확인할 수 없습니다."),
    VERIFICATION_EXPIRED(HttpStatus.CONFLICT, "확인 토큰이 만료되었습니다."),
    REFERENCE_NOT_PUBLIC(HttpStatus.UNPROCESSABLE_ENTITY, "공개 상태가 아닌 대상입니다."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 확인에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
