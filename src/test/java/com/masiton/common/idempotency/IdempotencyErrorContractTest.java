package com.masiton.common.idempotency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.masiton.common.idempotency.application.IdempotencyKeyReusedException;
import com.masiton.common.idempotency.application.InvalidIdempotencyKeyException;
import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.ErrorResponse;
import com.masiton.common.web.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("멱등성 공통 오류 계약")
class IdempotencyErrorContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/me/collections");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("잘못된 멱등성 키는 traceId를 포함한 400 오류로 변환한다")
    void 잘못된키_공통핸들러_400오류를반환한다() {
        // given
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "trace-invalid-key");

        // when
        var response = handler.handleBusinessException(new InvalidIdempotencyKeyException(), request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertError(response.getBody(), "INVALID_IDEMPOTENCY_KEY", "trace-invalid-key");
    }

    @Test
    @DisplayName("다른 본문에 재사용한 멱등성 키는 409 오류로 변환한다")
    void 다른본문재사용_공통핸들러_409오류를반환한다() {
        // given
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "trace-reused-key");

        // when
        var response = handler.handleBusinessException(new IdempotencyKeyReusedException(), request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertError(response.getBody(), "IDEMPOTENCY_KEY_REUSED", "trace-reused-key");
    }

    private void assertError(ErrorResponse error, String code, String traceId) {
        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.traceId()).isEqualTo(traceId);
    }
}
