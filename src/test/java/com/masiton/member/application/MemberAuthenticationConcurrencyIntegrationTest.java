package com.masiton.member.application;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.masiton.common.web.BusinessException;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;
import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("회원 인증 동시성")
class MemberAuthenticationConcurrencyIntegrationTest extends FullContextIntegrationTest {

    private static final String OLD_PASSWORD = "old correct horse battery staple";
    private static final String NEW_PASSWORD = "new correct horse battery staple";

    @Autowired
    private MemberAuthenticationService service;
    @Autowired
    private MemberAccountRepository accounts;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberActionTokenRepository actionTokens;
    @MockitoBean
    private MemberSessionStore sessions;
    @MockitoBean
    private MemberSessionRevocationStore revocations;
    @MockitoBean
    private MemberTokenIssuer tokenIssuer;

    @Test
    @DisplayName("재설정선행_이전비밀번호로그인은커밋후실패하고세션을만들지않는다")
    void 재설정선행_이전비밀번호로그인은커밋후실패하고세션을만들지않는다() throws Exception {
        // given
        MemberAccount account = activeAccount();
        String resetToken = "reset-" + UUID.randomUUID();
        given(actionTokens.consume(eq(resetToken), eq(MemberActionPurpose.PASSWORD_RESET), any(Instant.class)))
                .willReturn(Optional.of(resetToken(account.id())));
        CountDownLatch resetHasAccountLock = new CountDownLatch(1);
        CountDownLatch completeReset = new CountDownLatch(1);
        given(sessions.revokeAll(account.id().toString())).willAnswer(ignored -> {
            resetHasAccountLock.countDown();
            await(completeReset);
            return Set.of();
        });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> reset = executor.submit(() -> service.resetPassword(resetToken, NEW_PASSWORD));
            assertThat(resetHasAccountLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<MemberAuthenticationResult> login = executor.submit(() -> service.login(account.email(), OLD_PASSWORD, "127.0.0.1"));
            completeReset.countDown();

            reset.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> login.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(BusinessException.class);
            verify(sessions, never()).issue(eq(account.id().toString()), any());
        }
    }

    @Test
    @DisplayName("로그인선행_재설정은발급세션까지폐기한뒤비밀번호를변경한다")
    void 로그인선행_재설정은발급세션까지폐기한뒤비밀번호를변경한다() throws Exception {
        // given
        MemberAccount account = activeAccount();
        String sessionId = UUID.randomUUID().toString();
        String resetToken = "reset-" + UUID.randomUUID();
        CountDownLatch loginHasAccountLock = new CountDownLatch(1);
        CountDownLatch completeLogin = new CountDownLatch(1);
        given(sessions.issue(eq(account.id().toString()), any())).willAnswer(ignored -> {
            loginHasAccountLock.countDown();
            await(completeLogin);
            return new MemberSession(account.id().toString(), sessionId, "refresh-token", Set.of());
        });
        given(tokenIssuer.issueAccessToken(any())).willReturn("access-token");
        given(actionTokens.consume(eq(resetToken), eq(MemberActionPurpose.PASSWORD_RESET), any(Instant.class)))
                .willReturn(Optional.of(resetToken(account.id())));
        given(sessions.revokeAll(account.id().toString())).willReturn(Set.of(sessionId));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MemberAuthenticationResult> login = executor.submit(() -> service.login(account.email(), OLD_PASSWORD, "127.0.0.1"));
            assertThat(loginHasAccountLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> reset = executor.submit(() -> service.resetPassword(resetToken, NEW_PASSWORD));
            completeLogin.countDown();

            assertThat(login.get(5, TimeUnit.SECONDS).accessToken()).isEqualTo("access-token");
            reset.get(5, TimeUnit.SECONDS);
            verify(sessions).revokeAll(account.id().toString());
            verify(revocations).record(org.mockito.ArgumentMatchers.argThat(
                    revocation -> revocation.sessionId().toString().equals(sessionId)));
            assertThat(passwordEncoder.matches(NEW_PASSWORD,
                    accounts.findById(account.id()).orElseThrow().passwordHash())).isTrue();
        }
    }

    private MemberAccount activeAccount() {
        Instant now = Instant.now();
        String email = "member-" + UUID.randomUUID() + "@example.com";
        MemberAccount account = accounts.create(email, passwordEncoder.encode(OLD_PASSWORD), now);
        accounts.activate(account.id(), now);
        return accounts.findById(account.id()).orElseThrow();
    }

    private MemberActionToken resetToken(UUID memberId) {
        return new MemberActionToken(memberId, sha256("stored-token"), MemberActionPurpose.PASSWORD_RESET,
                Instant.now().plusSeconds(60));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent authentication flow");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent authentication flow", exception);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
