package com.masiton.ai.application.port.out;

public interface TemporaryInputCipher {
    EncryptedInput encrypt(String plaintext);

    String decrypt(EncryptedInput encryptedInput);

    record EncryptedInput(byte[] ciphertext, String keyId) {
    }
}
