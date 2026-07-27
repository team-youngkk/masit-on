package com.masiton.video.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface VideoRegistrationUseCase {
    VideoPreviewResult preview(VideoPreviewCommand command);
    VideoCreationResult create(VideoCreateCommand command);
    record VideoPreviewCommand(UUID adminAccountId, String sourceUrl) { }
    record VideoCreateCommand(UUID adminAccountId, String confirmationToken) { }
    record VideoPreviewResult(Decision decision, String confirmationToken, OffsetDateTime expiresAt, VideoCandidate candidate, ExistingVideo existingResource) {
        public enum Decision { READY, DUPLICATE, REVIEW_REQUIRED }
    }
    record VideoCandidate(UUID id, String title, String thumbnailUrl, String channelName, String sourceUrl) { }
    record ExistingVideo(UUID id, String title, String channelName, String sourceUrl) { }
    record VideoCreationResult(VideoCandidate video, boolean created, boolean duplicate) { }
}
