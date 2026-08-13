package com.masiton.ai.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.out.YoutubeChannelWatchSubscriptionPort;
import com.masiton.ai.application.port.out.YoutubeChannelWatchVerificationTokenPort;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.creator.application.port.in.CreatorReferenceExceptionFactory;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;

@Service
public class YoutubeChannelWatchManagementService implements YoutubeChannelWatchManagementUseCase {

    private final FindCreatorReferenceUseCase creatorReferences;
    private final YoutubeChannelWatchPersistenceService watchPersistence;
    private final YoutubeChannelWatchVerificationTokenPort verificationTokens;
    private final YoutubeChannelWatchSubscriptionPort subscriptions;

    public YoutubeChannelWatchManagementService(FindCreatorReferenceUseCase creatorReferences,
                                                YoutubeChannelWatchPersistenceService watchPersistence,
                                                YoutubeChannelWatchVerificationTokenPort verificationTokens,
                                                YoutubeChannelWatchSubscriptionPort subscriptions) {
        this.creatorReferences = creatorReferences;
        this.watchPersistence = watchPersistence;
        this.verificationTokens = verificationTokens;
        this.subscriptions = subscriptions;
    }

    @Override
    public WatchStatus setEnabled(UUID creatorId, boolean enabled) {
        FindCreatorReferenceUseCase.CreatorReference creator = creatorReferences.findCreatorReference(creatorId)
                .orElseThrow(this::creatorNotFound);
        if (enabled && (!creator.publiclyVisible() || !creator.externallyAvailable())) {
            throw creatorNotFound();
        }
        if (!enabled) {
            return status(watchPersistence.disable(creator.id(), creator.externalChannelId()));
        }
        Optional<com.masiton.ai.application.port.out.YoutubeChannelWatchStore.WatchDetail> active =
                watchPersistence.preserveActive(creator.id(), creator.externalChannelId());
        if (active.isPresent()) {
            return status(active.get());
        }
        String verificationToken = verificationTokens.issue(creator.externalChannelId());
        byte[] tokenHash = hashToken(verificationToken);
        YoutubeChannelWatchPersistenceService.ActivationPreparation preparation =
                watchPersistence.prepareActivation(creator.id(), creator.externalChannelId(), tokenHash);
        if (!preparation.subscriptionRequestRequired()) {
            return status(preparation.detail());
        }
        try {
            subscriptions.subscribe(creator.externalChannelId(), verificationToken);
        } catch (YoutubeChannelWatchSubscriptionFailedException exception) {
            if (isDefinitiveFailure(exception.category())) {
                watchPersistence.compensateExplicitFailure(creator.id(), creator.externalChannelId(), preparation);
            } else {
                watchPersistence.recordSubscriptionFailure(creator.externalChannelId(), exception.category(), tokenHash);
            }
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return status(preparation.detail());
    }

    private boolean isDefinitiveFailure(String category) {
        return "SUBSCRIPTION_4XX".equals(category)
                || "SUBSCRIPTION_5XX".equals(category)
                || "SUBSCRIPTION_UNEXPECTED_STATUS".equals(category);
    }

    private BusinessException creatorNotFound() {
        return CreatorReferenceExceptionFactory.notFound();
    }

    private WatchStatus status(com.masiton.ai.application.port.out.YoutubeChannelWatchStore.WatchDetail watch) {
        return new WatchStatus(watch.enabled(), watch.subscriptionStatus(), watch.lastNotificationAt(),
                watch.lastRenewedAt(), watch.lastErrorCategory());
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
