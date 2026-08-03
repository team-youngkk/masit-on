package com.masiton.member.application.port.out;

import java.util.Arrays;

import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;

public interface MemberActionTokenCipher {

    EncryptedToken encrypt(java.util.UUID memberActionTokenId, MemberActionPurpose purpose, String rawToken);

    String decrypt(MemberActionMailOutbox outbox);

    record EncryptedToken(byte[] ciphertext, byte[] nonce, String keyId) {
        public EncryptedToken {
            ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            nonce = Arrays.copyOf(nonce, nonce.length);
        }

        @Override
        public byte[] ciphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }

        @Override
        public byte[] nonce() {
            return Arrays.copyOf(nonce, nonce.length);
        }
    }
}
