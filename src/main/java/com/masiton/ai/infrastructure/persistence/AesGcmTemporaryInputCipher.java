package com.masiton.ai.infrastructure.persistence;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.common.web.BusinessException;

@Component
@EnableConfigurationProperties(TemporaryInputEncryptionProperties.class)
public class AesGcmTemporaryInputCipher implements TemporaryInputCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TemporaryInputEncryptionProperties properties;

    public AesGcmTemporaryInputCipher(TemporaryInputEncryptionProperties properties) {
        this.properties = properties;
    }

    @Override
    public EncryptedInput encrypt(String plaintext) {
        try {
            byte[] key = Base64.getDecoder().decode(properties.getActiveKey());
            if (key.length != 32 || properties.getActiveKeyId().isBlank()) {
                throw new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE", "Temporary input encryption is not configured.");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] ciphertext = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, ciphertext, 0, nonce.length);
            System.arraycopy(encrypted, 0, ciphertext, nonce.length, encrypted.length);
            return new EncryptedInput(ciphertext, properties.getActiveKeyId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE", "Temporary input encryption is not configured.");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AI temporary input encryption failed.", exception);
        }
    }
}
