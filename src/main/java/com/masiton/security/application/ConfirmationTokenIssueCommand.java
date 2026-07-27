package com.masiton.security.application;

import java.util.UUID;

import com.masiton.security.domain.model.ConfirmationTokenResourceType;

/**
 * A verified, server-produced preview that can be confirmed once. The snapshot is deliberately
 * retained as JSON text because it is the exact input for the later creation transaction.
 */
public record ConfirmationTokenIssueCommand(
        UUID adminAccountId,
        ConfirmationTokenResourceType resourceType,
        short candidateSchemaVersion,
        String identityKey,
        String candidateSnapshot) {
}
