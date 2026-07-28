package com.masiton.video.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Visit 등록에 필요한 Video의 최소 참조 Snapshot 계약이다. */
public interface FindVideoReferenceUseCase {

    Optional<VideoReference> findVideoReference(UUID videoId);

    record VideoReference(UUID id, UUID creatorId, String publisherExternalChannelId,
                          boolean publiclyVisible, boolean externallyAvailable) { }
}
