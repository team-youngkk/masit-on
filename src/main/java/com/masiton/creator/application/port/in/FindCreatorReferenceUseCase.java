package com.masiton.creator.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Visit 등록에 필요한 Creator의 공개 최소 Snapshot 계약이다. */
public interface FindCreatorReferenceUseCase {

    Optional<CreatorReference> findCreatorReference(UUID creatorId);

    record CreatorReference(UUID id, String externalChannelId, boolean publiclyVisible, boolean externallyAvailable) { }
}
