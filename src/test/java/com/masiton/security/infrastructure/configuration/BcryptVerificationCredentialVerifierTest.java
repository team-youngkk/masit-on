package com.masiton.security.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("검증 참여자 자격 증명 비교")
class BcryptVerificationCredentialVerifierTest {

    @Mock PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("로그인 ID가 달라도 bcrypt 비교를 항상 수행한다")
    void matches_다른로그인ID_bcrypt비교수행() {
        VerificationAccessProperties properties = properties();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        boolean matched = new BcryptVerificationCredentialVerifier(properties, passwordEncoder)
                .matches("unknown", "password");

        assertThat(matched).isFalse();
        verify(passwordEncoder).matches("password", properties.getPasswordHash());
    }

    @Test
    @DisplayName("설정 해시가 비정상이면 더미 bcrypt 비교 후 거부한다")
    void matches_비정상설정해시_더미비교후거부() {
        VerificationAccessProperties properties = properties();
        properties.setPasswordHash("");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThat(new BcryptVerificationCredentialVerifier(properties, passwordEncoder)
                .matches("participant", "password")).isFalse();
        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("password"), anyString());
    }

    @Test
    @DisplayName("검증 세션이 비활성화되어도 더미가 아닌 설정 해시로 비교하고 거부한다")
    void matches_검증세션비활성화_비밀번호비교후거부한다() {
        VerificationAccessProperties properties = properties();
        properties.setEnabled(false);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThat(new BcryptVerificationCredentialVerifier(properties, passwordEncoder)
                .matches("participant", "password")).isFalse();
        verify(passwordEncoder).matches("password", properties.getPasswordHash());
    }

    private VerificationAccessProperties properties() {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        properties.setLoginId("participant");
        properties.setPasswordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoOhi.0P8EIw1PhqcoUL24TJnS0W9TuP.2");
        return properties;
    }
}
