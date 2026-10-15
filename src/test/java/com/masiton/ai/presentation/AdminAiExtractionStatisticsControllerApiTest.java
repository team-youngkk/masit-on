package com.masiton.ai.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.test.FullContextIntegrationTest;

@AutoConfigureMockMvc
@DisplayName("관리자 AI 통계 API 보안과 입력 경계")
class AdminAiExtractionStatisticsControllerApiTest extends FullContextIntegrationTest {
    private static final String PATH = "/api/admin/ai/video-extractions/statistics";
    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("버전 NUL과 제어 문자는 400과 traceId를 반환한다")
    void 통계조회_버전제어문자_400과traceId를반환한다() throws Exception {
        for (String field : new String[]{"modelVersion", "promptVersion", "schemaVersion"}) {
            for (String value : new String[]{"x" + (char) 0, "x" + (char) 1 + "y", "x\n"}) {
                mvc.perform(get(PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-02T00:00:00Z").param(field, value))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
            }
        }
    }

    @Test
    @DisplayName("통계는 미인증 401 비관리자 403을 반환한다")
    void 통계조회_미인증비관리자_접근을거부한다() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(get(PATH).with(user("member").authorities(new SimpleGrantedAuthority("MEMBER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("관리자는 빈 기간 집계에서 0건과 null 비율을 받는다")
    void 통계조회_관리자빈기간_0건과null비율을반환한다() throws Exception {
        mvc.perform(get(PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-02T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.totalJobs").value(0))
                .andExpect(jsonPath("$.totals.blockedRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    @Test
    @DisplayName("기간 누락 파싱 실패 역전과 잘못된 유입 경로는 400을 반환한다")
    void 통계조회_잘못된입력_400과traceId를반환한다() throws Exception {
        mvc.perform(get(PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
        for (String to : new String[]{"invalid", "2026-09-01T00:00:00Z", "2028-01-01T00:00:00Z"}) {
            mvc.perform(get(PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .param("from", "2026-10-01T00:00:00Z").param("to", to))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
        }
        mvc.perform(get(PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-02T00:00:00Z").param("source", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
