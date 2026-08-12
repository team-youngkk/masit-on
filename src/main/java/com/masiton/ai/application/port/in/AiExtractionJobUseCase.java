package com.masiton.ai.application.port.in;

import java.net.URI;
import java.util.Optional;

import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

public interface AiExtractionJobUseCase {
    AiExtractionJobView submitAdmin(String videoUrl, String supplementText, String idempotencyKey);

    default AiExtractionJobView submitRetry(String videoUrl, String supplementText) {
        return submitRetry(videoUrl, supplementText, null);
    }

    AiExtractionJobView submitRetry(String videoUrl, String supplementText, String reason);

    Optional<AiExtractionJobView> submitWebhook(String channelId, String videoId, URI videoUrl);

    String verifyChallenge(String channelId, String verifyToken, String challenge);
}
