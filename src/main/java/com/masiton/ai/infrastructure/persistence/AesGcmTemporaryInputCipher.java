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
import com.masiton.ai.application.port.out.TemporaryInputDecryptionException;
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
            String activeKeyId = properties.getActiveKeyId();
            byte[] key = encryptionKey(activeKeyId);
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] ciphertext = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, ciphertext, 0, nonce.length);
            System.arraycopy(encrypted, 0, ciphertext, nonce.length, encrypted.length);
            return new EncryptedInput(ciphertext, activeKeyId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE", "Temporary input encryption is not configured.");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AI temporary input encryption failed.", exception);
        }
    }

    @Override
    public String decrypt(EncryptedInput encryptedInput) {
        try {
            if (encryptedInput == null || encryptedInput.keyId() == null || encryptedInput.keyId().isBlank()) {
                throw TemporaryInputDecryptionException.invalidInput(null);
            }
            byte[] key = decryptionKey(encryptedInput.keyId());
            byte[] ciphertext = encryptedInput.ciphertext();
            if (ciphertext == null || ciphertext.length <= NONCE_BYTES) {
                throw TemporaryInputDecryptionException.invalidInput(null);
            }
            byte[] nonce = java.util.Arrays.copyOfRange(ciphertext, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(ciphertext, NONCE_BYTES, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (TemporaryInputDecryptionException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw TemporaryInputDecryptionException.keyUnavailable(exception);
        } catch (GeneralSecurityException exception) {
            throw TemporaryInputDecryptionException.invalidInput(exception);
        }
    }

    private byte[] encryptionKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw unavailable();
        }
        String encodedKey = properties.getKeys().get(keyId);
        if (encodedKey == null || encodedKey.isBlank()) {
            throw unavailable();
        }
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw unavailable();
        }
        return key;
    }

    private byte[] decryptionKey(String keyId) {
        String encodedKey = properties.getKeys().get(keyId);
        if (encodedKey == null || encodedKey.isBlank()) {
            throw TemporaryInputDecryptionException.keyUnavailable(null);
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                throw TemporaryInputDecryptionException.keyUnavailable(null);
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw TemporaryInputDecryptionException.keyUnavailable(exception);
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE", "Temporary input encryption is not configured.");
    }
}
