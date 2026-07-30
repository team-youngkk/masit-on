package com.masiton.member.presentation;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.masiton.common.security.MemberCookieSettings;
import com.masiton.member.application.MemberAuthenticationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("회원 인증 API 응답 계약")
class MemberAuthenticationControllerTest {

    private final MemberAuthenticationService service = mock(MemberAuthenticationService.class);
    private final MemberAuthenticationController controller = new MemberAuthenticationController(
            service,
            new MemberCookieSettings(
                    "__Secure-masiton-member-refresh",
                    Duration.ofDays(14),
                    "/api/auth/tokens",
                    true,
                    "Strict",
                    "https://example.test"
            )
    );

    @Test
    @DisplayName("회원가입은 상태 비노출 접수 본문을 반환한다")
    void 회원가입_유효요청_접수본문반환() {
        var response = controller.register(new MemberAuthenticationController.CredentialsRequest(
                "member@example.com", "correct horse battery staple"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isEqualTo(new MemberAuthenticationController.AcceptedResponse(true));
        verify(service).register("member@example.com", "correct horse battery staple");
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 newPassword 필드를 서비스에 전달한다")
    void 비밀번호재설정_확인_newPassword전달() {
        var response = controller.resetPassword(new MemberAuthenticationController.ResetPasswordRequest(
                "reset-token", "new correct horse battery staple"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).resetPassword("reset-token", "new correct horse battery staple");
    }
}
