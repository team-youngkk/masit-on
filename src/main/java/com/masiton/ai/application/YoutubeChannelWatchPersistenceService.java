package com.masiton.ai.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;

@Service
public class YoutubeChannelWatchPersistenceService {

    private final YoutubeChannelWatchStore watchStore;

    public YoutubeChannelWatchPersistenceService(YoutubeChannelWatchStore watchStore) {
        this.watchStore = watchStore;
    }

    @Transactional(readOnly = true)
    public Optional<YoutubeChannelWatchStore.WatchDetail> findDetail(String channelId) {
        return watchStore.findDetail(channelId);
    }

    @Transactional
    public ActivationPreparation prepareActivation(UUID creatorId, String channelId, byte[] tokenHash) {
        Optional<YoutubeChannelWatchStore.Watch> existing = watchStore.findForUpdate(channelId);
        if (existing.isPresent() && existing.get().acceptsNotifications()
                && existing.get().subscriptionTokenHash() != null) {
            YoutubeChannelWatchStore.WatchDetail detail = watchStore.upsert(
                    creatorId, channelId, true, "ACTIVE", existing.get().subscriptionTokenHash());
            return new ActivationPreparation(detail, false, existing, tokenHash);
        }
        YoutubeChannelWatchStore.WatchDetail detail = watchStore.upsert(
                creatorId, channelId, true, "UNKNOWN", tokenHash);
        return new ActivationPreparation(detail, true, existing, tokenHash);
    }

    @Transactional
    public Optional<YoutubeChannelWatchStore.WatchDetail> preserveActive(UUID creatorId, String channelId) {
        Optional<YoutubeChannelWatchStore.Watch> existing = watchStore.findForUpdate(channelId);
        if (existing.isEmpty() || !existing.get().acceptsNotifications()
                || existing.get().subscriptionTokenHash() == null) {
            return Optional.empty();
        }
        return Optional.of(watchStore.upsert(creatorId, channelId, true, "ACTIVE",
                existing.get().subscriptionTokenHash()));
    }

    @Transactional
    public YoutubeChannelWatchStore.WatchDetail disable(UUID creatorId, String channelId) {
        return watchStore.upsert(creatorId, channelId, false, "INACTIVE", null);
    }

    @Transactional
    public Optional<YoutubeChannelWatchStore.WatchDetail> recordSubscriptionFailure(
            String channelId, String errorCategory, byte[] expectedTokenHash) {
        return watchStore.markSubscriptionFailed(channelId, errorCategory, expectedTokenHash);
    }

    @Transactional
    public void compensateExplicitFailure(UUID creatorId, String channelId, ActivationPreparation preparation) {
        if (preparation.previous().isPresent()) {
            watchStore.restoreActivation(creatorId, channelId, preparation.previous().get(),
                    preparation.pendingTokenHash());
        } else {
            watchStore.deletePending(channelId, preparation.pendingTokenHash());
        }
    }

    public record ActivationPreparation(YoutubeChannelWatchStore.WatchDetail detail,
                                        boolean subscriptionRequestRequired,
                                        Optional<YoutubeChannelWatchStore.Watch> previous,
                                        byte[] pendingTokenHash) { }
}
