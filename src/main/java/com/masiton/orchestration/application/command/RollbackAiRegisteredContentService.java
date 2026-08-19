package com.masiton.orchestration.application.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.orchestration.application.port.in.RollbackAiRegisteredContentUseCase;
import com.masiton.orchestration.application.port.out.AiRegisteredContentStore;

@Service
public class RollbackAiRegisteredContentService implements RollbackAiRegisteredContentUseCase {
    private final AiRegisteredContentStore store;

    public RollbackAiRegisteredContentService(AiRegisteredContentStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void rollback(RegistrationReference reference) {
        store.makePrivateIfCreated(reference.snapshotId(), reference.restaurantId(), reference.restaurantCreated(),
                reference.creatorId(), reference.creatorCreated(), reference.videoId(), reference.videoCreated(),
                reference.visitId(), reference.visitCreated());
    }

    @Override
    @Transactional
    public void discardFailedRegistration(UUID restaurantId, boolean restaurantCreated,
                                          UUID creatorId, boolean creatorCreated,
                                          UUID videoId, boolean videoCreated,
                                          UUID visitId, boolean visitCreated) {
        store.deleteIfCreated(restaurantId, restaurantCreated, creatorId, creatorCreated, videoId, videoCreated,
                visitId, visitCreated);
    }
}
