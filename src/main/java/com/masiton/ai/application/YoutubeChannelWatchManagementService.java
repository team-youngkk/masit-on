package com.masiton.ai.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.ai.application.port.out.YoutubeChannelWatchVerificationTokenPort;
import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.CreatorReferenceExceptionFactory;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;

@Service
public class YoutubeChannelWatchManagementService implements YoutubeChannelWatchManagementUseCase {

    private final FindCreatorReferenceUseCase creatorReferences;
    private final YoutubeChannelWatchStore watchStore;
    private final YoutubeChannelWatchVerificationTokenPort verificationTokens;

    public YoutubeChannelWatchManagementService(FindCreatorReferenceUseCase creatorReferences,
                                                YoutubeChannelWatchStore watchStore,
                                                YoutubeChannelWatchVerificationTokenPort verificationTokens) {
        this.creatorReferences = creatorReferences;
        this.watchStore = watchStore;
        this.verificationTokens = verificationTokens;
    }

    @Override
    @Transactional
    public WatchStatus setEnabled(UUID creatorId, boolean enabled) {
        FindCreatorReferenceUseCase.CreatorReference creator = creatorReferences.findCreatorReference(creatorId)
                .orElseThrow(this::creatorNotFound);
        if (enabled && (!creator.publiclyVisible() || !creator.externallyAvailable())) {
            throw creatorNotFound();
        }
        String subscriptionStatus = enabled ? "UNKNOWN" : "INACTIVE";
        byte[] subscriptionTokenHash = enabled
                ? hashToken(verificationTokens.issue(creator.externalChannelId()))
                : null;
        YoutubeChannelWatchStore.WatchDetail watch = watchStore.upsert(
                creator.id(), creator.externalChannelId(), enabled, subscriptionStatus, subscriptionTokenHash);
        return new WatchStatus(watch.enabled(), watch.subscriptionStatus(), watch.lastNotificationAt(),
                watch.lastRenewedAt(), watch.lastErrorCategory());
    }

    private BusinessException creatorNotFound() {
        return CreatorReferenceExceptionFactory.notFound();
    }

    private byte[] hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("YouTube verification token must not be blank.");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
