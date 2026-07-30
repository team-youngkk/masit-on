package com.masiton.member.infrastructure.mail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.masiton.member.application.port.out.MemberActionTokenCipher;
import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.infrastructure.configuration.MemberActionMailProperties;

@Component
public class AesGcmMemberActionTokenCipher implements MemberActionTokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;
    private final SecureRandom secureRandom;

    @Autowired
    public AesGcmMemberActionTokenCipher(MemberActionMailProperties properties) {
        this(properties, new SecureRandom());
    }

    AesGcmMemberActionTokenCipher(MemberActionMailProperties properties, SecureRandom secureRandom) {
        this.activeKeyId = properties.getActiveKeyId();
        this.keys = properties.getKeys().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> new SecretKeySpec(decodeAesKey(entry.getValue(), entry.getKey()), "AES")
        ));
        this.secureRandom = secureRandom;
    }

    @Override
    public EncryptedToken encrypt(UUID memberActionTokenId, MemberActionPurpose purpose, String rawToken) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyFor(activeKeyId), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(memberActionTokenId, purpose));
            return new EncryptedToken(cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)), nonce, activeKeyId);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt member action token", exception);
        }
    }

    @Override
    public String decrypt(MemberActionMailOutbox outbox) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyFor(outbox.encryptionKeyId()),
                    new GCMParameterSpec(GCM_TAG_BITS, outbox.encryptionNonce()));
            cipher.updateAAD(aad(outbox.memberActionTokenId(), outbox.purpose()));
            return new String(cipher.doFinal(outbox.encryptedToken()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt member action token", exception);
        }
    }

    private SecretKey keyFor(String keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("No member action-mail encryption key is configured for key id " + keyId);
        }
        return key;
    }

    private byte[] decodeAesKey(String encodedKey, String keyId) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalStateException("Member action-mail encryption key has an invalid length for key id " + keyId);
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Member action-mail encryption key is not Base64 for key id " + keyId, exception);
        }
    }

    private byte[] aad(UUID memberActionTokenId, MemberActionPurpose purpose) {
        return (memberActionTokenId + ":" + purpose.name()).getBytes(StandardCharsets.UTF_8);
    }
}
