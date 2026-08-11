package com.masiton.ai.application.port.out;

public class AiProviderException extends RuntimeException {

    private final AiProviderFailureCategory category;

    public AiProviderException(AiProviderFailureCategory category) {
        this(category, null);
    }

    public AiProviderException(AiProviderFailureCategory category, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
    }

    public AiProviderFailureCategory category() {
        return category;
    }
}
