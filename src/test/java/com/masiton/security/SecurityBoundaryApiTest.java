package com.masiton.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 API 보안 경계")
class SecurityBoundaryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("관리자 API는 인증 없이 401 공통 오류를 반환한다")
    void 관리자API_미인증_401공통오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("관리자 권한이 없는 인증 주체는 403 공통 오류를 반환한다")
    void 관리자API_관리자권한없음_403공통오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("공개 조회 경로는 인증 필터에서 거부하지 않는다")
    void 공개조회_미인증_보안경계에서거부하지않는다() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isNotFound());
    }
}
