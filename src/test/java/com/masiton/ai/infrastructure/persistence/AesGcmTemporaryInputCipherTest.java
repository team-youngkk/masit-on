package com.masiton.ai.infrastructure.persistence;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.TemporaryInputDecryptionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AI 임시 입력 AES-GCM 암호화")
class AesGcmTemporaryInputCipherTest {
    private static final String OLD_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String NEW_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    @Test
    @DisplayName("암호화한 임시 입력을 같은 키 ID로 복호화한다")
    void 암복호화_같은키_원문을복원한다() {
        AesGcmTemporaryInputCipher cipher = cipher("active-1", NEW_KEY, Map.of());

        TemporaryInputCipher.EncryptedInput encrypted = cipher.encrypt("관리자 보완 텍스트");

        assertThat(cipher.decrypt(encrypted)).isEqualTo("관리자 보완 텍스트");
    }

    @Test
    @DisplayName("활성 키가 교체돼도 저장된 키 ID로 과거 임시 입력을 복호화한다")
    void 복호화_활성키교체_과거키로복호화한다() {
        TemporaryInputCipher.EncryptedInput encrypted = cipher("old-1", OLD_KEY, Map.of())
                .encrypt("관리자 보완 텍스트");

        AesGcmTemporaryInputCipher rotatedCipher = cipher(
                "new-1", NEW_KEY, Map.of("old-1", OLD_KEY, "new-1", NEW_KEY));

        assertThat(rotatedCipher.decrypt(encrypted)).isEqualTo("관리자 보완 텍스트");
        assertThat(rotatedCipher.encrypt("새 입력").keyId()).isEqualTo("new-1");
    }

    @Test
    @DisplayName("저장된 키 ID에 해당하는 과거 키가 없으면 fail-closed로 차단한다")
    void 복호화_과거키누락_차단한다() {
        TemporaryInputCipher.EncryptedInput encrypted = cipher("old-1", OLD_KEY, Map.of())
                .encrypt("관리자 보완 텍스트");

        AesGcmTemporaryInputCipher rotatedCipher = cipher("new-1", NEW_KEY, Map.of());

        assertThatThrownBy(() -> rotatedCipher.decrypt(encrypted))
                .isInstanceOf(TemporaryInputDecryptionException.class)
                .extracting(exception -> ((TemporaryInputDecryptionException) exception).retryable())
                .isEqualTo(true);
    }

    @Test
    @DisplayName("암호문이 변조되면 GCM 검증 실패를 입력 오류로 분류한다")
    void 복호화_암호문변조_입력오류로차단한다() {
        AesGcmTemporaryInputCipher cipher = cipher("active-1", NEW_KEY, Map.of());
        TemporaryInputCipher.EncryptedInput encrypted = cipher.encrypt("관리자 보완 텍스트");
        byte[] tampered = encrypted.ciphertext();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(new TemporaryInputCipher.EncryptedInput(
                tampered, encrypted.keyId())))
                .isInstanceOf(TemporaryInputDecryptionException.class)
                .extracting(exception -> ((TemporaryInputDecryptionException) exception).retryable())
                .isEqualTo(false);
    }

    private AesGcmTemporaryInputCipher cipher(String activeKeyId, String activeKey, Map<String, String> keys) {
        TemporaryInputEncryptionProperties properties = new TemporaryInputEncryptionProperties();
        properties.setActiveKeyId(activeKeyId);
        properties.setActiveKey(activeKey);
        properties.setKeys(keys);
        return new AesGcmTemporaryInputCipher(properties);
    }
}
