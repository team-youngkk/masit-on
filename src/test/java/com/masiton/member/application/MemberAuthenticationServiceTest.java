package com.masiton.member.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.masiton.common.security.MemberJwtSettings;
import com.masiton.common.web.BusinessException;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionMailOutboxStore;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.application.port.out.MemberActionTokenCipher;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.member.application.port.out.MemberDeletionJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;
import com.masiton.member.domain.model.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberAuthenticationService")
class MemberAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");
    private static final Pattern EMAIL_VERIFICATION_CODE_PATTERN = Pattern.compile("^[A-HJ-NP-Z2-9]{8}$");
    private static final Pattern PASSWORD_RESET_TOKEN_PATTERN =
            Pattern.compile("^[0-9a-f\\-]{36}-[0-9a-f\\-]{36}$");

    @Mock
    private MemberAccountRepository accounts;
    @Mock
    private MemberActionTokenRepository actionTokens;
    @Mock
    private MemberActionTokenDeliveryPort actionTokenDelivery;
    @Mock
    private MemberActionMailOutboxStore actionMailOutbox;
    @Mock
    private MemberActionTokenCipher actionTokenCipher;
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
        given(rateLimits.isLoginBlocked("member@example.com", "127.0.0.1")).willReturn(false);
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
        verify(rateLimits, never()).tryRecordLoginFailure(any(), any());
    }

    @Test
    @DisplayName("로그인_잘못된 비밀번호는 실패를 기록하고 세션을 발급하지 않는다")
    void 로그인_잘못된비밀번호_실패기록() {
        // given
        MemberAccount account = activeAccount(UUID.randomUUID());
        given(rateLimits.isLoginBlocked("member@example.com", "127.0.0.1")).willReturn(false);
        given(accounts.findByEmailForUpdate("member@example.com")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("wrong-password", account.passwordHash())).willReturn(false);

        // when & then
        assertInvalidCredentials(() -> service().login(
                "member@example.com", "wrong-password", "127.0.0.1"));
        verify(rateLimits).tryRecordLoginFailure("member@example.com", "127.0.0.1");
        verifyNoInteractions(sessions);
    }

    @Test
    @DisplayName("로그인_없는 계정도 더미 BCrypt 해시를 비교하고 실패를 기록한다")
    void 로그인_없는계정_더미해시비교와실패기록() {
        // given
        given(accounts.findByEmailForUpdate("missing@example.com")).willReturn(Optional.empty());
        given(rateLimits.isLoginBlocked("missing@example.com", "127.0.0.1")).willReturn(false);

        // when & then
        assertInvalidCredentials(() -> service().login(
                "missing@example.com", "any-password", "127.0.0.1"));
        verify(passwordEncoder).matches(
                org.mockito.ArgumentMatchers.eq("any-password"),
                org.mockito.ArgumentMatchers.startsWith("$2a$10$"));
        verify(rateLimits).tryRecordLoginFailure("missing@example.com", "127.0.0.1");
        verifyNoInteractions(sessions);
    }

    @Test
    @DisplayName("로그인_요청 제한 중이면 저장소와 BCrypt를 호출하지 않고 동일한 오류를 반환한다")
    void 로그인_요청제한_동일오류반환() {
        // given
        given(rateLimits.isLoginBlocked("member@example.com", "127.0.0.1")).willReturn(true);

        // when & then
        assertInvalidCredentials(() -> service().login(
                "member@example.com", "any-password", "127.0.0.1"));
        verify(rateLimits).isLoginBlocked("member@example.com", "127.0.0.1");
        verify(accounts, never()).findByEmailForUpdate(any());
        verifyNoInteractions(passwordEncoder, sessions);
        verifyNoMoreInteractions(rateLimits);
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
    @DisplayName("이메일 인증은 ASCII 공백과 소문자를 정규화한 뒤 사용한다")
    void 이메일인증_ASCII공백소문자_정규화후사용() {
        UUID memberId = UUID.randomUUID();
        given(rateLimits.acquireEmailVerificationAttempt("127.0.0.1"))
                .willReturn(MemberRateLimitStore.VerificationAttemptResult.permit());
        given(actionTokens.consume("AB7K9M2Q", MemberActionPurpose.EMAIL_VERIFICATION, NOW))
                .willReturn(Optional.of(new MemberActionToken(
                        memberId, new byte[] {1}, MemberActionPurpose.EMAIL_VERIFICATION, NOW.plusSeconds(60))));

        service().verifyEmail("  ab7k9m2q  ", "127.0.0.1");

        verify(rateLimits).acquireEmailVerificationAttempt("127.0.0.1");
        verify(actionTokens).consume("AB7K9M2Q", MemberActionPurpose.EMAIL_VERIFICATION, NOW);
        verify(accounts).activate(memberId, NOW);
    }

    @Test
    @DisplayName("이메일 인증 제출은 형식 검증 전에 출처 제한을 먼저 적용한다")
    void 이메일인증_형식검증전_출처제한적용() {
        given(rateLimits.acquireEmailVerificationAttempt("127.0.0.1"))
                .willReturn(MemberRateLimitStore.VerificationAttemptResult.permit());

        assertThatThrownBy(() -> service().verifyEmail(" invalid-token ", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");
                });

        verify(rateLimits).acquireEmailVerificationAttempt("127.0.0.1");
        verifyNoInteractions(actionTokens, accounts);
    }

    @Test
    @DisplayName("이메일 인증 제출은 token 필드가 없어도 출처 제한을 소모한다")
    void 이메일인증_token필드누락_출처제한을소모한다() {
        given(rateLimits.acquireEmailVerificationAttempt("127.0.0.1"))
                .willReturn(MemberRateLimitStore.VerificationAttemptResult.permit());

        assertThatThrownBy(() -> service().verifyEmail(null, "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("MISSING_REQUIRED_FIELD");
                });

        verify(rateLimits).acquireEmailVerificationAttempt("127.0.0.1");
        verifyNoInteractions(actionTokens, accounts);
    }

    @Test
    @DisplayName("이메일 인증 제출 제한을 초과하면 429와 Retry-After를 반환한다")
    void 이메일인증_제출제한초과_429RetryAfter반환() {
        given(rateLimits.acquireEmailVerificationAttempt("127.0.0.1"))
                .willReturn(MemberRateLimitStore.VerificationAttemptResult.reject(600));

        assertThatThrownBy(() -> service().verifyEmail("AB7K9M2Q", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
                    assertThat(exception.retryAfterSeconds()).isEqualTo(600);
                });

        verify(rateLimits).acquireEmailVerificationAttempt("127.0.0.1");
        verifyNoInteractions(actionTokens, accounts);
    }

    @Test
    @DisplayName("이메일 인증 경로의 Redis 또는 DB 장애는 503으로 변환한다")
    void 이메일인증_Redis또는DB장애_503반환() {
        given(rateLimits.acquireEmailVerificationAttempt("127.0.0.1"))
                .willReturn(MemberRateLimitStore.VerificationAttemptResult.permit());
        given(actionTokens.consume("AB7K9M2Q", MemberActionPurpose.EMAIL_VERIFICATION, NOW))
                .willThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service().verifyEmail("AB7K9M2Q", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("AUTHENTICATION_SERVICE_UNAVAILABLE");
                });
    }

    @Test
    @DisplayName("로그인_계정저장소장애면인증서비스이용불가로변환한다")
    void 로그인_계정저장소장애면인증서비스이용불가로변환한다() {
        // given
        given(rateLimits.isLoginBlocked("member@example.com", "127.0.0.1")).willReturn(false);
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
        given(actionTokenCipher.encrypt(any(), any(), any())).willReturn(
                new MemberActionTokenCipher.EncryptedToken(new byte[17], new byte[12], "test-1"));

        service().register("member@example.com", "correct horse battery staple", "127.0.0.1");

        org.mockito.InOrder persistence = inOrder(actionTokens, actionMailOutbox);
        persistence.verify(actionTokens).replace(any(MemberActionToken.class), org.mockito.ArgumentMatchers.eq(NOW));
        persistence.verify(actionMailOutbox).enqueue(any(MemberActionMailOutbox.class), org.mockito.ArgumentMatchers.eq(NOW));
        verifyNoInteractions(actionTokenDelivery);
    }

    @Test
    @DisplayName("회원가입 이메일 인증 코드는 혼동 문자를 제외한 8자 코드로 발급한다")
    void 회원가입_이메일인증코드_8자허용문자발급() {
        MemberAccount account = new MemberAccount(UUID.randomUUID(), "member@example.com", "password-hash",
                MemberStatus.PENDING_VERIFICATION, null, null, NOW);
        given(rateLimits.tryAcquireAccountActionRequest("member@example.com", "127.0.0.1")).willReturn(true);
        given(passwordEncoder.encode("correct horse battery staple")).willReturn("password-hash");
        given(accounts.createIfAbsent("member@example.com", "password-hash", NOW)).willReturn(Optional.of(account));
        given(actionTokenCipher.encrypt(any(), any(), any())).willReturn(
                new MemberActionTokenCipher.EncryptedToken(new byte[17], new byte[12], "test-1"));

        service().register("member@example.com", "correct horse battery staple", "127.0.0.1");

        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionTokenCipher).encrypt(any(), org.mockito.ArgumentMatchers.eq(MemberActionPurpose.EMAIL_VERIFICATION),
                rawTokenCaptor.capture());
        assertThat(rawTokenCaptor.getValue()).matches(EMAIL_VERIFICATION_CODE_PATTERN);
    }

    @Test
    @DisplayName("인증 메일 재발송은 이메일 전용 제한기를 사용하고 출처 제한기는 사용하지 않는다")
    void 인증메일재발송_이메일전용제한기_사용() {
        MemberAccount account = new MemberAccount(UUID.randomUUID(), "member@example.com", "password-hash",
                MemberStatus.PENDING_VERIFICATION, null, null, NOW);
        given(rateLimits.tryAcquireEmailRequest("member@example.com")).willReturn(true);
        given(accounts.findByEmail("member@example.com")).willReturn(Optional.of(account));
        given(actionTokenCipher.encrypt(any(), any(), any())).willReturn(
                new MemberActionTokenCipher.EncryptedToken(new byte[17], new byte[12], "test-1"));

        service().resendVerification("member@example.com", "127.0.0.1");

        verify(rateLimits).tryAcquireEmailRequest("member@example.com");
        verify(rateLimits, never()).tryAcquireAccountActionRequest(any(), any());
        verify(actionMailOutbox).enqueue(any(MemberActionMailOutbox.class), org.mockito.ArgumentMatchers.eq(NOW));
        verifyNoInteractions(actionTokenDelivery);
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 토큰과 outbox만 저장하고 직접 전송하지 않는다")
    void 비밀번호재설정요청_outbox저장_직접전송없음() {
        MemberAccount account = activeAccount(UUID.randomUUID());
        given(rateLimits.tryAcquireAccountActionRequest("member@example.com", "127.0.0.1")).willReturn(true);
        given(accounts.findByEmail("member@example.com")).willReturn(Optional.of(account));
        given(actionTokenCipher.encrypt(any(), any(), any())).willReturn(
                new MemberActionTokenCipher.EncryptedToken(new byte[17], new byte[12], "test-1"));

        service().requestPasswordReset("member@example.com", "127.0.0.1");

        verify(actionTokens).replace(any(MemberActionToken.class), org.mockito.ArgumentMatchers.eq(NOW));
        verify(actionMailOutbox).enqueue(any(MemberActionMailOutbox.class), org.mockito.ArgumentMatchers.eq(NOW));
        verifyNoInteractions(actionTokenDelivery);
    }

    @Test
    @DisplayName("비밀번호 재설정 토큰은 기존 UUID-UUID 불투명 형식을 유지한다")
    void 비밀번호재설정요청_UUID_UUID형식유지() {
        MemberAccount account = activeAccount(UUID.randomUUID());
        given(rateLimits.tryAcquireAccountActionRequest("member@example.com", "127.0.0.1")).willReturn(true);
        given(accounts.findByEmail("member@example.com")).willReturn(Optional.of(account));
        given(actionTokenCipher.encrypt(any(), any(), any())).willReturn(
                new MemberActionTokenCipher.EncryptedToken(new byte[17], new byte[12], "test-1"));

        service().requestPasswordReset("member@example.com", "127.0.0.1");

        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionTokenCipher).encrypt(any(), org.mockito.ArgumentMatchers.eq(MemberActionPurpose.PASSWORD_RESET),
                rawTokenCaptor.capture());
        assertThat(rawTokenCaptor.getValue()).matches(PASSWORD_RESET_TOKEN_PATTERN);
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
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.code()).isEqualTo("INTERNAL_SERVER_ERROR");
                });

        verify(revocationRecoveryJobs).enqueue(
                new MemberSessionRevocation(sessionId, NOW, NOW.plus(Duration.ofDays(14))), NOW);
        verify(sessions, never()).revoke(memberId.toString(), sessionId.toString());
    }

    @Test
    @DisplayName("Redis 세션 폐기 실패는 인증 서비스 장애로 구분한다")
    void logout_Redis세션폐기실패_503반환() {
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        given(sessions.findSession("refresh-token"))
                .willReturn(Optional.of(new MemberSessionOwner(memberId.toString(), sessionId.toString())));
        doThrow(new IllegalStateException("redis unavailable")).when(sessions)
                .revoke(memberId.toString(), sessionId.toString());

        assertThatThrownBy(() -> service().logout(
                new MemberPrincipal(memberId.toString(), sessionId.toString()), NOW.plusSeconds(60), "refresh-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("AUTHENTICATION_SERVICE_UNAVAILABLE");
                });
    }

    private MemberAuthenticationService service() {
        return new MemberAuthenticationService(accounts, actionTokens, actionMailOutbox, actionTokenCipher,
                rateLimits, deletionJobs, sessions,
                revocationRecoveryJobs, revocations,
                tokenIssuer, passwordEncoder,
                new MemberJwtSettings("issuer", "member", Duration.ofMinutes(30), "key-id"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void assertInvalidCredentials(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    private MemberAccount activeAccount(UUID memberId) {
        return new MemberAccount(memberId, "member@example.com", "password-hash", MemberStatus.ACTIVE, NOW, null, NOW);
    }
}
