package com.masiton.creator.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CreatorRegistrationUseCase {
    CreatorPreviewResult preview(CreatorPreviewCommand command);
    CreatorCreationResult create(CreatorCreateCommand command);

    record CreatorPreviewCommand(UUID adminAccountId, String channelUrl) { }
    record CreatorCreateCommand(UUID adminAccountId, String confirmationToken) { }
    record CreatorPreviewResult(Decision decision, String confirmationToken, OffsetDateTime expiresAt,
                                CreatorCandidate candidate, ExistingCreator existingResource) {
        public enum Decision { READY, DUPLICATE, REVIEW_REQUIRED }
    }
    record CreatorCandidate(UUID id, String channelName, String channelUrl) { }
    record ExistingCreator(UUID id, String channelName, String channelUrl) { }
    record CreatorCreationResult(CreatorCandidate creator, boolean created, boolean duplicate) { }
}
