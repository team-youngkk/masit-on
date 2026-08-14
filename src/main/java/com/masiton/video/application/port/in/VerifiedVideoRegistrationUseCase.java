package com.masiton.video.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 관리자 확인 없이 자동 검증된 Video를 정식 등록하는 내부 Port다. */
public interface VerifiedVideoRegistrationUseCase {

    RegistrationResult register(VerifiedVideoCommand command);

    record VerifiedVideoCommand(
            UUID creatorId,
            String externalVideoId,
            String publisherExternalChannelId,
            String title,
            String sourceUrl,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            OffsetDateTime checkedAt) {
    }

    record RegistrationResult(UUID videoId, boolean created) {
    }
}
