package com.masiton.member.infrastructure.mail;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.member.application.port.out.MemberActionTokenCipher;
import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.infrastructure.configuration.MemberActionMailProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("회원 Action Token AES-GCM 암호화")
class AesGcmMemberActionTokenCipherTest {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    @DisplayName("12-byte nonce와 token id·purpose AAD로 암호화한 값만 복호화한다")
    void encrypt_12바이트nonce와AAD_암호화복호화() {
        UUID tokenId = UUID.randomUUID();
        MemberActionTokenCipher.EncryptedToken encrypted = cipher().encrypt(
                tokenId, MemberActionPurpose.EMAIL_VERIFICATION, "raw-token");
        MemberActionMailOutbox outbox = new MemberActionMailOutbox(
                UUID.randomUUID(), tokenId, MemberActionPurpose.EMAIL_VERIFICATION,
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyId());

        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(encrypted.ciphertext()).isNotEqualTo("raw-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(cipher().decrypt(outbox)).isEqualTo("raw-token");
    }

    @Test
    @DisplayName("purpose가 변경되면 AAD 검증에 실패한다")
    void decrypt_AAD변조_실패() {
        UUID tokenId = UUID.randomUUID();
        MemberActionTokenCipher.EncryptedToken encrypted = cipher().encrypt(
                tokenId, MemberActionPurpose.EMAIL_VERIFICATION, "raw-token");
        MemberActionMailOutbox tampered = new MemberActionMailOutbox(
                UUID.randomUUID(), tokenId, MemberActionPurpose.PASSWORD_RESET,
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyId());

        assertThatThrownBy(() -> cipher().decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    private AesGcmMemberActionTokenCipher cipher() {
        MemberActionMailProperties properties = new MemberActionMailProperties();
        properties.setActiveKeyId("test-1");
        properties.setActiveKey(TEST_KEY);
        properties.validate();
        return new AesGcmMemberActionTokenCipher(properties);
    }
}
