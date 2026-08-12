package com.masiton.ai.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;

@Service
public class YoutubeChannelWatchManagementService implements YoutubeChannelWatchManagementUseCase {

    private final FindCreatorReferenceUseCase creatorReferences;
    private final YoutubeChannelWatchStore watchStore;

    public YoutubeChannelWatchManagementService(FindCreatorReferenceUseCase creatorReferences,
                                                YoutubeChannelWatchStore watchStore) {
        this.creatorReferences = creatorReferences;
        this.watchStore = watchStore;
    }

    @Override
    @Transactional
    public WatchStatus setEnabled(UUID creatorId, boolean enabled) {
        FindCreatorReferenceUseCase.CreatorReference creator = creatorReferences.findCreatorReference(creatorId)
                .orElseThrow(this::creatorNotFound);
        if (enabled && (!creator.publiclyVisible() || !creator.externallyAvailable())) {
            throw creatorNotFound();
        }
        String subscriptionStatus = enabled ? "ACTIVE" : "INACTIVE";
        YoutubeChannelWatchStore.WatchDetail watch = watchStore.upsert(
                creator.id(), creator.externalChannelId(), enabled, subscriptionStatus);
        return new WatchStatus(watch.enabled(), watch.subscriptionStatus(), watch.lastNotificationAt(),
                watch.lastRenewedAt(), watch.lastErrorCategory());
    }

    private BusinessException creatorNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND", "요청한 유튜버를 찾을 수 없습니다.");
    }
}
