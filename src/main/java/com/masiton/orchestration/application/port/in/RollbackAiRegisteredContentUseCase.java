package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/** AI 자동 등록으로 새로 생성된 공개 콘텐츠만 사후 롤백한다. */
public interface RollbackAiRegisteredContentUseCase {

    void rollback(RegistrationReference reference);

    record RegistrationReference(
            UUID snapshotId,
            UUID restaurantId, boolean restaurantCreated,
            UUID creatorId, boolean creatorCreated,
            UUID videoId, boolean videoCreated,
            UUID visitId, boolean visitCreated) {
    }
}
