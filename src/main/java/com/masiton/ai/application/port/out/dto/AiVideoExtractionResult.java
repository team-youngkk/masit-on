package com.masiton.ai.application.port.out.dto;

import java.util.Objects;

import tools.jackson.databind.JsonNode;

/** Normalized S1 candidate payload. Provider response envelopes are intentionally excluded. */
public record AiVideoExtractionResult(JsonNode candidates, String providerRequestId) {

    public AiVideoExtractionResult {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (!candidates.isObject()) {
            throw new IllegalArgumentException("candidates must be a JSON object");
        }
    }
}
