package com.masiton.ai.application.port.out;

public interface TemporaryInputCipher {
    EncryptedInput encrypt(String plaintext);

    record EncryptedInput(byte[] ciphertext, String keyId) {
    }
}
