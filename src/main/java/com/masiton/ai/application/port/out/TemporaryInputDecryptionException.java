package com.masiton.ai.application.port.out;

public class TemporaryInputDecryptionException extends RuntimeException {
    private final boolean retryable;

    private TemporaryInputDecryptionException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static TemporaryInputDecryptionException invalidInput(Throwable cause) {
        return new TemporaryInputDecryptionException("AI temporary input is invalid.", false, cause);
    }

    public static TemporaryInputDecryptionException keyUnavailable(Throwable cause) {
        return new TemporaryInputDecryptionException("AI temporary input decryption key is unavailable.", true, cause);
    }

    public boolean retryable() {
        return retryable;
    }
}
