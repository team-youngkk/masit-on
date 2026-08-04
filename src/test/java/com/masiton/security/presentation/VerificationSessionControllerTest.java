package com.masiton.security.presentation;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.security.application.VerificationSessionService;
import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;
import com.masiton.security.infrastructure.web.VerificationClientAddressResolver;

@ExtendWith(MockitoExtension.class)
class VerificationSessionControllerTest {

    @Mock VerificationSessionService service;
    @Mock VerificationClientAddressResolver clientAddressResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VerificationSessionController(service, properties, clientAddressResolver))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("로그인 성공은 계약 속성의 7일 검증 쿠키만 발급한다")
    void 세션생성_정상요청_보안쿠키를발급한다() throws Exception {
        when(service.create("participant", "valid-password", "198.51.100.20")).thenReturn("raw-session");
        when(clientAddressResolver.resolve(any())).thenReturn("198.51.100.20");

        mockMvc.perform(post("/api/verification/sessions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header("X-Forwarded-For", "198.51.100.20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"participant\",\"password\":\"valid-password\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("__Host-masiton-verification=raw-session"),
                        containsString("Path=/"),
                        containsString("Max-Age=604800"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        not(containsString("Domain=")))));
    }

    @Test
    @DisplayName("다른 Origin의 세션 생성은 거부한다")
    void 세션생성_다른Origin_403을반환한다() throws Exception {
        mockMvc.perform(post("/api/verification/sessions")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"participant\",\"password\":\"valid-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("세션이 없는 종료도 쿠키를 만료하고 멱등 성공한다")
    void 세션종료_쿠키없음_204를반환한다() throws Exception {
        mockMvc.perform(delete("/api/verification/sessions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("Max-Age=0")));
        verify(service).revoke(null);
    }

    @Test
    @DisplayName("쿠키가 있는 종료는 그 세션 ID로 서비스를 호출하고 쿠키를 만료한다")
    void 세션종료_쿠키있음_해당세션ID로서비스호출() throws Exception {
        mockMvc.perform(delete("/api/verification/sessions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .cookie(new jakarta.servlet.http.Cookie("__Host-masiton-verification", "raw-session")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(service).revoke("raw-session");
    }

    @Test
    @DisplayName("종료 중 Redis 장애는 503 JSON으로 응답한다")
    void 세션종료_Redis장애_503JSON반환() throws Exception {
        org.mockito.Mockito.doThrow(new com.masiton.common.web.BusinessException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "VALIDATION_SESSION_UNAVAILABLE", "검증 세션을 확인할 수 없습니다."))
                .when(service).revoke("raw-session");

        mockMvc.perform(delete("/api/verification/sessions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .cookie(new jakarta.servlet.http.Cookie("__Host-masiton-verification", "raw-session")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VALIDATION_SESSION_UNAVAILABLE"));
    }

    @Test
    @DisplayName("내부 오류 Adapter는 traceId가 있는 API 401 JSON을 반환한다")
    void 접근오류_내부Adapter_JSON401을반환한다() throws Exception {
        mockMvc.perform(get("/internal/verification/access-required"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("VALIDATION_ACCESS_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
