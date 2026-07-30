package com.masiton.member.presentation;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.masiton.member.application.MemberAuthenticationService;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("현재 회원 API 응답 계약")
class MemberProfileControllerTest {

    private final MemberAuthenticationService service = mock(MemberAuthenticationService.class);
    private final MemberProfileController controller = new MemberProfileController(service);

    @Test
    @DisplayName("현재 회원 조회는 최소 정보와 private no-store를 반환한다")
    void 현재회원조회_최소정보와캐시금지반환() {
        UUID id = UUID.randomUUID();
        when(service.currentMember(id.toString())).thenReturn(new MemberAccount(
                id, "member@example.com", "hash", MemberStatus.ACTIVE, Instant.now(), null, Instant.now()));

        var response = controller.current(authentication(id));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getBody()).isEqualTo(new MemberProfileController.MemberResponse(id.toString(), "member@example.com"));
    }

    @Test
    @DisplayName("회원 탈퇴 요청은 비동기 접수 상태를 반환한다")
    void 회원탈퇴_접수_202반환() {
        assertThat(controller.requestDeletion(authentication(UUID.randomUUID())).getStatusCode().value()).isEqualTo(202);
    }

    private JwtAuthenticationToken authentication(UUID memberId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(1800),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", memberId.toString(), "sid", UUID.randomUUID().toString()));
        return new JwtAuthenticationToken(jwt);
    }
}
