package com.masiton.ai.application.port.out;

public class AiProviderException extends RuntimeException {

    private final AiProviderFailureCategory category;
    private final boolean retryable;

    public AiProviderException(AiProviderFailureCategory category) {
        this(category, null);
    }

    public AiProviderException(AiProviderFailureCategory category, Throwable cause) {
        this(category, defaultRetryable(category), cause);
    }

    public AiProviderException(AiProviderFailureCategory category, boolean retryable) {
        this(category, retryable, null);
    }

    public AiProviderException(AiProviderFailureCategory category, boolean retryable, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
        this.retryable = retryable;
    }

    public AiProviderFailureCategory category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }

    private static boolean defaultRetryable(AiProviderFailureCategory category) {
        return category == AiProviderFailureCategory.TIMEOUT
                || category == AiProviderFailureCategory.RATE_LIMIT
                || category == AiProviderFailureCategory.UPSTREAM;
    }
}
