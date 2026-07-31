package com.masiton.common.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.masiton.common.observability.TraceIdFilter;

/**
 * 표준 MVC 예외는 {@link ResponseEntityExceptionHandler}가 정한 상태 코드를 유지하고 본문만 공통 오류 계약으로 바꾼다.
 * 잘못된 입력과 없는 자원을 500으로 집계하지 않기 위한 것이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PRIVATE_NO_STORE = "private, no-store";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        log.warn("business error: code={}", exception.code());
        ResponseEntity.BodyBuilder response = response(exception.status(), request);
        if (exception.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()));
        }
        return response.body(ErrorResponse.of(exception.code(), exception.getMessage(), exception.fieldErrors(), traceId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_FIELD_VALUE;
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(exception.getName(), "허용되지 않는 값입니다.")
        );
        return response(errorCode.status(), request)
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage(), errors, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("unhandled error: type={}", exception.getClass().getSimpleName());
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return response(errorCode.status(), request)
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage(), traceId()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_FIELD_VALUE;
        List<ErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(errorCode.status(), request)
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage(), errors, traceId()));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_FIELD;
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(exception.getParameterName(), "필수 요청 값이 누락되었습니다.")
        );
        return response(errorCode.status(), request)
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage(), errors, traceId()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ErrorCode errorCode = resolveErrorCode(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("unhandled framework error: type={}", exception.getClass().getSimpleName());
        }
        return response(statusCode, request)
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage(), traceId()));
    }

    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        if (statusCode.equals(HttpStatus.NOT_FOUND)) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (statusCode.is4xxClientError()) {
            return ErrorCode.INVALID_REQUEST;
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

    private String traceId() {
        return MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
    }

    private ResponseEntity.BodyBuilder response(HttpStatusCode status, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (isMemberPrivatePath(request)) {
            response.header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE);
        }
        return response;
    }

    private ResponseEntity.BodyBuilder response(HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return response(status, servletWebRequest.getRequest());
        }
        return ResponseEntity.status(status);
    }

    private boolean isMemberPrivatePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.equals("/api/me") || requestUri.startsWith("/api/me/");
    }
}
