package com.masiton.common.web;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors,
        Object resource,
        String traceId
) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, List.of(), null, traceId);
    }

    public static ErrorResponse of(
            String code,
            String message,
            List<FieldError> errors,
            String traceId
    ) {
        return new ErrorResponse(code, message, List.copyOf(errors), null, traceId);
    }
}
