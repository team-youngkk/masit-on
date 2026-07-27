package com.masiton.security.application.port.in;

import java.util.UUID;

import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.ConfirmationTokenIssueCommand;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;

/**
 * Cross-domain contract for issuing and consuming registration confirmation tokens.
 */
public interface ConfirmationTokenUseCase {

    IssuedConfirmationToken issue(ConfirmationTokenIssueCommand command);

    /**
     * Must be called inside the resource creation transaction. The acquired database row remains
     * locked until the caller creates/replays the resource and completes the token.
     */
    AcquiredConfirmationToken acquire(
            String rawToken,
            UUID adminAccountId,
            ConfirmationTokenResourceType expectedResourceType);

    void completeCreated(UUID tokenId, UUID resourceId);

    void completeDuplicate(UUID tokenId, UUID resourceId);
}
