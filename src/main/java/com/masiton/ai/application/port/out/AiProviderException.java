package com.masiton.ai.application.port.out;

public class AiProviderException extends RuntimeException {

    private final AiProviderFailureCategory category;
    private final boolean retryable;
    private final Integer httpStatus;

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
        this(category, retryable, cause, null);
    }

    public AiProviderException(AiProviderFailureCategory category, boolean retryable, Throwable cause,
                               Integer httpStatus) {
        super(category.name(), cause);
        this.category = category;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public AiProviderFailureCategory category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    private static boolean defaultRetryable(AiProviderFailureCategory category) {
        return category == AiProviderFailureCategory.TIMEOUT
                || category == AiProviderFailureCategory.RATE_LIMIT
                || category == AiProviderFailureCategory.UPSTREAM
                || category == AiProviderFailureCategory.SERVER_ERROR
                || category == AiProviderFailureCategory.NETWORK_ERROR;
    }
}
