package com.masiton.security;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 API 보안 경계")
class SecurityBoundaryApiTest extends FullContextIntegrationTest {

    private static final String UNKNOWN_CREATOR_ID = "00000000-0000-4000-8000-000000000000";

    /*
     * {"alg":"RS256"}.{"sub":"someone","aud":"other","exp":1}.서명 형태의 값이다. 실제 키로 서명하지
     * 않았으므로 Resource Server가 해석을 시도하면 만료·audience 불일치 Token과 같은 검증 실패로
     * 끝난다. 비밀값이 아니다.
     */
    private static final String UNVERIFIABLE_JWT =
            "eyJhbGciOiJSUzI1NiJ9"
                    + ".eyJzdWIiOiJzb21lb25lIiwiYXVkIjoib3RoZXIiLCJleHAiOjF9"
                    + ".c2lnbmF0dXJl";

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
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유튜버 상세 세 조회는 인증 없이 보안 경계를 통과한다")
    void 유튜버상세공개조회_미인증_보안경계에서거부하지않는다() throws Exception {
        mockMvc.perform(get("/api/creators/" + UNKNOWN_CREATOR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        mockMvc.perform(get("/api/creators/" + UNKNOWN_CREATOR_ID + "/restaurants"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        mockMvc.perform(get("/api/creators/" + UNKNOWN_CREATOR_ID + "/videos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    /*
     * 공개 조회는 Bearer Token을 해석하지 않는다. 만료·다른 audience Token이 섞여 들어와도
     * 401로 막히면 공개 계약이 인증 상태에 종속된다.
     *
     * <p>JWT 형태를 갖췄지만 이 서버의 키로 검증할 수 없는 값을 함께 보낸다. 만료·audience 불일치
     * Token도 검증 단계에서 같은 실패로 끝나므로, Token을 해석하는 순간 401이 되는 회귀를 이 값으로
     * 잡을 수 있다. 세 경로를 모두 확인해 isCreatorDetailReadRequest의 경로 파싱이 하위 경로 하나를
     * 놓치는 경우를 드러낸다.
     */
    @Test
    @DisplayName("유튜버 상세 세 조회는 검증할 수 없는 Bearer Token이 있어도 401을 반환하지 않는다")
    void 유튜버상세공개조회_검증불가Bearer토큰_401을반환하지않는다() throws Exception {
        List<String> paths = List.of(
                "/api/creators/" + UNKNOWN_CREATOR_ID,
                "/api/creators/" + UNKNOWN_CREATOR_ID + "/restaurants",
                "/api/creators/" + UNKNOWN_CREATOR_ID + "/videos");
        List<String> tokens = List.of("not-a-valid-token", UNVERIFIABLE_JWT);

        for (String path : paths) {
            for (String token : tokens) {
                mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
            }
        }
    }

    /*
     * API-POPULAR-001도 유튜버 상세와 같은 회원 문맥 없는 완전 공개 조회다. 검증할 수 없는
     * Bearer Token이 섞여 들어와도 Token을 해석하는 순간 401이 되는 회귀를 이 값으로 잡는다.
     */
    @Test
    @DisplayName("인기 맛집 공개 조회는 검증할 수 없는 Bearer Token이 있어도 401을 반환하지 않는다")
    void 인기맛집공개조회_검증불가Bearer토큰_401을반환하지않는다() throws Exception {
        List<String> tokens = List.of("not-a-valid-token", UNVERIFIABLE_JWT);

        for (String token : tokens) {
            mockMvc.perform(get("/api/restaurants/popular").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("정의되지 않은 유튜버 하위 경로는 기본 거부한다")
    void 유튜버하위경로_정의되지않음_미인증_401공통오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators/" + UNKNOWN_CREATOR_ID + "/subscribers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /*
     * PR #139 재발 방지: API-DISCOVERY-COURSE-001도 회원 문맥이 없는 완전 공개 POST 조회다.
     * 검증할 수 없는 Bearer Token이 섞여 들어와도 Token을 해석하는 순간 401이 되는 회귀를 이 값으로 잡는다.
     */
    @Test
    @DisplayName("맛집 코스 추천 공개 조회는 검증할 수 없는 Bearer Token이 있어도 401을 반환하지 않는다")
    void 코스추천공개조회_검증불가Bearer토큰_401을반환하지않는다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/restaurants/course-routes")
                        .header("Authorization", "Bearer " + UNVERIFIABLE_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("맛집 코스 추천 공개 조회는 인증 헤더가 없어도 401 또는 403을 반환하지 않는다")
    void 코스추천공개조회_인증헤더없음_401또는403을반환하지않는다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/restaurants/course-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}
