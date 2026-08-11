package com.masiton.ai.application.port.out.dto;

import java.net.URI;
import java.util.Objects;

public record AiVideoExtractionRequest(URI videoUrl, String supplementText) {

    public AiVideoExtractionRequest {
        Objects.requireNonNull(videoUrl, "videoUrl must not be null");
        if (!"https".equalsIgnoreCase(videoUrl.getScheme())) {
            throw new IllegalArgumentException("videoUrl must use HTTPS");
        }
        supplementText = supplementText == null ? "" : supplementText.trim();
    }
}
