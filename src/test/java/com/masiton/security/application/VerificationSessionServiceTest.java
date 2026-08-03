package com.masiton.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.masiton.common.web.BusinessException;
import com.masiton.security.application.port.out.VerificationAccessStore;
import com.masiton.security.application.port.out.VerificationCredentialVerifier;
import com.masiton.security.application.port.out.VerificationSessionSettings;
import com.masiton.security.application.port.out.VerificationStoreUnavailableException;

@ExtendWith(MockitoExtension.class)
class VerificationSessionServiceTest {

    @Mock VerificationAccessStore store;
    @Mock VerificationCredentialVerifier credentialVerifier;
    @Mock VerificationSessionSettings settings;
    private VerificationSessionService service;

    @BeforeEach
    void setUp() {
        service = new VerificationSessionService(store, settings, credentialVerifier);
    }

    @Test
    @DisplayName("정상 자격 증명은 256비트 세션을 발급하고 7일 고정 만료로 저장한다")
    void 세션생성_정상자격증명_원문을7일만료로저장한다() {
        when(credentialVerifier.matches("participant", "secret")).thenReturn(true);
        when(settings.sessionTtl()).thenReturn(Duration.ofDays(7));

        String rawSessionId = service.create("participant", "secret", "127.0.0.1");

        assertThat(rawSessionId).hasSize(43);
        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(store).save(id.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofDays(7)));
        assertThat(id.getValue()).isEqualTo(rawSessionId);
        verify(store).clearFailures("participant", "127.0.0.1");
    }

    @Test
    @DisplayName("잘못된 자격 증명은 등록 여부와 무관한 401을 반환하고 실패를 기록한다")
    void 세션생성_잘못된자격증명_일반화된오류를반환한다() {
        when(credentialVerifier.matches("unknown", "wrong")).thenReturn(false);

        assertThatThrownBy(() -> service.create("unknown", "wrong", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("INVALID_VALIDATION_CREDENTIALS");
                    assertThat(exception.status().value()).isEqualTo(401);
                });
        verify(store).recordFailure("unknown", "127.0.0.1");
        verify(store, never()).save(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Redis 장애는 검증 세션 장애 503으로 닫힌다")
    void 세션검증_Redis장애_503으로차단한다() {
        when(store.exists("raw-session")).thenThrow(new VerificationStoreUnavailableException(new RuntimeException("down")));

        assertThatThrownBy(() -> service.isValid("raw-session"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("VALIDATION_SESSION_UNAVAILABLE");
                    assertThat(exception.status().value()).isEqualTo(503);
                });
    }
}
