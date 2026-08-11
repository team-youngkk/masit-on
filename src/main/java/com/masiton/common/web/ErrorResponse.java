package com.masiton.common.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors,
        Object resource,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Object details,
        String traceId
) {

    public record FieldError(String field, String reason) {
    }

    public record ResourceReference(String requestId, String status) {
    }

    /**
     * 기능별 오류 계약이 정의하는 안전한 추가 정보다. 기존 자원 참조인 {@code resource}와 의미를 섞지 않는다.
     */
    public ErrorResponse(
            String code,
            String message,
            List<FieldError> errors,
            Object resource,
            String traceId
    ) {
        this(code, message, errors, resource, null, traceId);
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, List.of(), null, null, traceId);
    }

    public static ErrorResponse of(
            String code,
            String message,
            List<FieldError> errors,
            String traceId
    ) {
        return new ErrorResponse(code, message, List.copyOf(errors), null, null, traceId);
    }
}
