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
import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.member.application.port.out.MemberDeletionJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;
import com.masiton.member.domain.model.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

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
    private MemberRateLimitStore rateLimits;
    @Mock
    private MemberDeletionJobStore deletionJobs;
    @Mock
    private MemberSessionStore sessions;
    @Mock
    private MemberSessionRevocationRecoveryJobStore revocationRecoveryJobs;
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
        MemberAuthenticationResult result = service.login("MEMBER@example.com", "correct-password", "127.0.0.1");

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

    @Test
    @DisplayName("로그인_계정저장소장애면인증서비스이용불가로변환한다")
    void 로그인_계정저장소장애면인증서비스이용불가로변환한다() {
        // given
        given(accounts.findByEmailForUpdate("member@example.com"))
                .willThrow(new IllegalStateException("database unavailable"));
        MemberAuthenticationService service = service();

        // when & then
        assertThatThrownBy(() -> service.login("member@example.com", "correct-password", "127.0.0.1"))
                .isInstanceOf(com.masiton.common.web.BusinessException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "AUTHENTICATION_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("회원가입_메일전송실패여도접수처리를완료한다")
    void 회원가입_메일전송실패_접수처리완료() {
        MemberAccount account = new MemberAccount(UUID.randomUUID(), "member@example.com", "password-hash",
                MemberStatus.PENDING_VERIFICATION, null, null, NOW);
        given(rateLimits.tryAcquireAccountActionRequest("member@example.com", "127.0.0.1")).willReturn(true);
        given(passwordEncoder.encode("correct horse battery staple")).willReturn("password-hash");
        given(accounts.createIfAbsent("member@example.com", "password-hash", NOW)).willReturn(Optional.of(account));
        doThrow(new IllegalStateException("smtp unavailable")).when(actionTokenDelivery)
                .send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        service().register("member@example.com", "correct horse battery staple", "127.0.0.1");

        org.mockito.Mockito.verifyNoInteractions(actionTokens);
    }

    @Test
    @DisplayName("로그아웃 폐기는 PostgreSQL 복구 작업을 먼저 기록하고 성공 뒤 제거한다")
    void logout_세션폐기_내구복구작업선기록후제거() {
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        given(sessions.findSession("refresh-token"))
                .willReturn(Optional.of(new MemberSessionOwner(memberId.toString(), sessionId.toString())));

        service().logout(new MemberPrincipal(memberId.toString(), sessionId.toString()), NOW.plusSeconds(60), "refresh-token");

        MemberSessionRevocation expected = new MemberSessionRevocation(sessionId, NOW, NOW.plus(Duration.ofDays(14)));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(revocations, sessions);
        order.verify(revocations).record(expected);
        order.verify(sessions).revoke(memberId.toString(), sessionId.toString());
        org.mockito.Mockito.verifyNoInteractions(revocationRecoveryJobs);
    }

    @Test
    @DisplayName("폐기 marker 저장 실패는 복구 작업을 보상적으로 기록한다")
    void logout_marker저장실패_복구작업보상기록() {
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        given(sessions.findSession("refresh-token"))
                .willReturn(Optional.of(new MemberSessionOwner(memberId.toString(), sessionId.toString())));
        doThrow(new IllegalStateException("database unavailable")).when(revocations)
                .record(org.mockito.ArgumentMatchers.any(MemberSessionRevocation.class));

        assertThatThrownBy(() -> service().logout(
                new MemberPrincipal(memberId.toString(), sessionId.toString()), NOW.plusSeconds(60), "refresh-token"))
                .isInstanceOf(com.masiton.common.web.BusinessException.class);

        verify(revocationRecoveryJobs).enqueue(
                new MemberSessionRevocation(sessionId, NOW, NOW.plus(Duration.ofDays(14))), NOW);
    }

    private MemberAuthenticationService service() {
        return new MemberAuthenticationService(accounts, actionTokens, actionTokenDelivery, rateLimits, deletionJobs, sessions,
                revocationRecoveryJobs, revocations,
                tokenIssuer, passwordEncoder,
                new MemberJwtSettings("issuer", "member", Duration.ofMinutes(30), "key-id"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MemberAccount activeAccount(UUID memberId) {
        return new MemberAccount(memberId, "member@example.com", "password-hash", MemberStatus.ACTIVE, NOW, null, NOW);
    }
}
