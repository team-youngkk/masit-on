package com.masiton.security.application;

import java.util.UUID;

import com.masiton.security.domain.model.ConfirmationTokenStatus;

/**
 * Locked token state exposed to a resource application service. An ISSUED token supplies the
 * immutable verified snapshot; a completed token supplies the resource id to replay.
 */
public record AcquiredConfirmationToken(
        UUID tokenId,
        short candidateSchemaVersion,
        String identityKey,
        String candidateSnapshot,
        ConfirmationTokenStatus status,
        UUID resultResourceId) {

    public boolean isReplay() {
        return status == ConfirmationTokenStatus.CREATED || status == ConfirmationTokenStatus.DUPLICATE;
    }
}
