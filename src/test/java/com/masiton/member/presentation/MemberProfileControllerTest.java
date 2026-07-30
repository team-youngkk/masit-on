package com.masiton.member.presentation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.common.web.BusinessException;
import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("현재 회원 API 응답 계약")
class MemberProfileControllerTest {

    private final MemberAuthenticationService service = mock(MemberAuthenticationService.class);
    private final MemberProfileController controller = new MemberProfileController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("현재 회원 조회는 최소 정보와 private no-store를 반환한다")
    void 현재회원조회_최소정보와캐시금지반환() {
        UUID id = UUID.randomUUID();
        when(service.currentMember(id.toString())).thenReturn(new MemberAccount(
                id,
                "member@example.com",
                "hash",
                MemberStatus.ACTIVE,
                Instant.now(),
                null,
                Instant.now()
        ));

        var response = controller.current(authentication(id));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getBody()).isEqualTo(new MemberProfileController.MemberResponse(id.toString(), "member@example.com"));
    }

    @Test
    @DisplayName("회원 탈퇴 요청은 202와 private no-store를 반환한다")
    void 회원탈퇴_접수_202와캐시금지반환() {
        UUID memberId = UUID.randomUUID();

        var response = controller.requestDeletion(authentication(memberId));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        verify(service).requestDeletion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("현재 회원 오류는 private no-store와 공통 오류 본문을 반환한다")
    void 현재회원오류_privateNoStore오류본문반환() throws Exception {
        UUID memberId = UUID.randomUUID();
        doThrow(new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication is required"
        )).when(service).currentMember(memberId.toString());

        mockMvc.perform(get("/api/me")
                        .principal(authentication(memberId)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("현재 회원의 예기치 않은 오류도 private no-store를 반환한다")
    void 현재회원예기치않은오류_privateNoStore오류본문반환() throws Exception {
        UUID memberId = UUID.randomUUID();
        doThrow(new IllegalStateException("unexpected")).when(service).currentMember(memberId.toString());

        mockMvc.perform(get("/api/me")
                        .principal(authentication(memberId)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("현재 회원의 허용되지 않은 메서드 오류도 private no-store를 반환한다")
    void 현재회원허용되지않은메서드_privateNoStore오류본문반환() throws Exception {
        mockMvc.perform(post("/api/me"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private JwtAuthenticationToken authentication(UUID memberId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(1800),
                Map.of("alg", "none"),
                Map.of("sub", memberId.toString(), "sid", UUID.randomUUID().toString())
        );
        return new JwtAuthenticationToken(jwt);
    }
}
