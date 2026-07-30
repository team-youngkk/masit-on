package com.masiton.member.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.masiton.common.security.MemberJwtSettings;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;
import com.masiton.member.domain.model.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberAuthenticationService")
class MemberAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    @Mock
    private MemberAccountRepository accounts;
    @Mock
    private MemberActionTokenRepository actionTokens;
    @Mock
    private MemberActionTokenDeliveryPort actionTokenDelivery;
    @Mock
    private MemberSessionStore sessions;
    @Mock
    private MemberSessionRevocationStore revocations;
    @Mock
    private MemberTokenIssuer tokenIssuer;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("로그인_회원행잠금으로인증하고세션을발급한다")
    void 로그인_회원행잠금으로인증하고세션을발급한다() {
        // given
        UUID memberId = UUID.randomUUID();
        MemberAccount account = activeAccount(memberId);
        given(accounts.findByEmailForUpdate("member@example.com")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("correct-password", account.passwordHash())).willReturn(true);
        given(sessions.issue(memberId.toString(), Duration.ofDays(14)))
                .willReturn(new MemberSession(memberId.toString(), "session-id", "refresh-token", Set.of()));
        given(tokenIssuer.issueAccessToken(new MemberPrincipal(memberId.toString(), "session-id"))).willReturn("access-token");
        MemberAuthenticationService service = service();

        // when
        MemberAuthenticationResult result = service.login("MEMBER@example.com", "correct-password");

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(accounts).findByEmailForUpdate("member@example.com");
    }

    @Test
    @DisplayName("비밀번호재설정_회원행잠금후기존세션을폐기하고비밀번호를변경한다")
    void 비밀번호재설정_회원행잠금후기존세션을폐기하고비밀번호를변경한다() {
        // given
        UUID memberId = UUID.randomUUID();
        MemberAccount account = activeAccount(memberId);
        given(actionTokens.consume("reset-token", MemberActionPurpose.PASSWORD_RESET, NOW))
                .willReturn(Optional.of(new MemberActionToken(memberId, new byte[] {1}, MemberActionPurpose.PASSWORD_RESET,
                        NOW.plusSeconds(60))));
        given(accounts.findByIdForUpdate(memberId)).willReturn(Optional.of(account));
        given(sessions.revokeAll(memberId.toString())).willReturn(Set.of());
        given(passwordEncoder.encode("new-password")).willReturn("new-password-hash");
        MemberAuthenticationService service = service();

        // when
        service.resetPassword("reset-token", "new-password");

        // then
        verify(accounts).findByIdForUpdate(memberId);
        verify(accounts).changePassword(memberId, "new-password-hash", NOW);
    }

    private MemberAuthenticationService service() {
        return new MemberAuthenticationService(accounts, actionTokens, actionTokenDelivery, sessions, revocations,
                tokenIssuer, passwordEncoder,
                new MemberJwtSettings("issuer", "member", Duration.ofMinutes(30), "key-id"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MemberAccount activeAccount(UUID memberId) {
        return new MemberAccount(memberId, "member@example.com", "password-hash", MemberStatus.ACTIVE, NOW, null, NOW);
    }
}
