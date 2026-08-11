package com.masiton.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;
import com.masiton.common.observability.TraceIdFilter;
import com.masiton.test.FullContextIntegrationTest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("공통 오류 계약")
class ErrorContractApiTest extends FullContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("검증 실패는 400과 필드 오류 목록을 반환한다")
    void 요청검증_필수값누락_400과필드오류를반환한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/test-support/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.resource").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"details\"");
    }

    @Test
    @DisplayName("해석할 수 없는 본문은 400 INVALID_REQUEST를 반환한다")
    void 요청본문파싱_잘못된JSON_400을반환한다() throws Exception {
        mockMvc.perform(post("/test-support/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락은 400 MISSING_REQUIRED_FIELD를 반환한다")
    void 쿼리파라미터_필수값누락_400을반환한다() throws Exception {
        mockMvc.perform(get("/test-support/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("허용되지 않는 타입의 값은 400 INVALID_FIELD_VALUE를 반환한다")
    void 쿼리파라미터_타입불일치_400을반환한다() throws Exception {
        mockMvc.perform(get("/test-support/required-param").param("size", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("정의되지 않은 경로는 500이 아닌 404를 반환한다")
    void 경로조회_정의되지않은경로_404를반환한다() throws Exception {
        mockMvc.perform(get("/test-support/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("업무 예외는 지정한 상태와 코드로 변환된다")
    void 업무예외_발생_지정한상태와코드를반환한다() throws Exception {
        mockMvc.perform(get("/test-support/business-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFERENCE_NOT_PUBLIC"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 정보를 감춘 500으로 변환된다")
    void 예상못한예외_발생_내부정보없는500을반환한다() throws Exception {
        mockMvc.perform(get("/test-support/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다."))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("요청마다 서로 다른 traceId가 발급된다")
    void traceId발급_서로다른요청_매번다른값을반환한다() throws Exception {
        String first = extractTraceId();
        String second = extractTraceId();

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    @Test
    @DisplayName("오류 응답의 traceId가 대응 로그에도 같은 값으로 남는다")
    void traceId기록_서버오류_응답과로그가같은값을가진다() throws Exception {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);

        try {
            String body = mockMvc.perform(get("/test-support/unexpected-error"))
                    .andExpect(status().isInternalServerError())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String responseTraceId = JsonPath.read(body, "$.traceId");

            assertThat(appender.list)
                    .isNotEmpty()
                    .anySatisfy(event ->
                            assertThat(event.getMDCPropertyMap())
                                    .containsEntry(TraceIdFilter.TRACE_ID_MDC_KEY, responseTraceId));
        } finally {
            handlerLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("예상하지 못한 예외의 민감한 메시지는 로그에 남지 않는다")
    void 예외로그_민감정보포함_원문을기록하지않는다() throws Exception {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);

        try {
            mockMvc.perform(get("/test-support/unexpected-error"))
                    .andExpect(status().isInternalServerError());

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains("admin-password") || message.contains("access-token"));
        } finally {
            handlerLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("요청 처리가 끝나면 traceId가 MDC에 남지 않는다")
    void traceId정리_요청종료_MDC가비어있다() throws Exception {
        mockMvc.perform(get("/test-support/business-error"));

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    private String extractTraceId() throws Exception {
        String body = mockMvc.perform(get("/test-support/business-error"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.traceId");
    }

    @TestConfiguration
    static class TestSupportConfiguration {

        @Bean
        TestSupportController testSupportController() {
            return new TestSupportController();
        }
    }

    @RestController
    static class TestSupportController {

        @PostMapping("/test-support/echo")
        String echo(@Valid @RequestBody EchoRequest request) {
            return request.name();
        }

        @GetMapping("/test-support/required-param")
        int requiredParam(@RequestParam int size) {
            return size;
        }

        @GetMapping("/test-support/business-error")
        String businessError() {
            throw new BusinessException(ErrorCode.REFERENCE_NOT_PUBLIC);
        }

        @GetMapping("/test-support/unexpected-error")
        String unexpectedError() {
            throw new IllegalStateException("password=admin-password, authorization=Bearer access-token");
        }
    }

    record EchoRequest(@NotBlank String name) {
    }
}
