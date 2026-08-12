package com.masiton.orchestration.application.port.out;

import java.util.UUID;

public interface AiRegisteredContentStore {
    void makePrivateIfCreated(UUID snapshotId, UUID restaurantId, boolean created,
                              UUID creatorId, boolean creatorCreated,
                              UUID videoId, boolean videoCreated,
                              UUID visitId, boolean visitCreated);
}
