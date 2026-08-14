package com.masiton.creator.application.port.in;

import java.util.UUID;

/** 관리자 확인 없이 자동 검증된 Creator를 정식 등록하는 내부 Port다. */
public interface VerifiedCreatorRegistrationUseCase {

    RegistrationResult register(VerifiedCreatorCommand command);

    record VerifiedCreatorCommand(
            String externalChannelId,
            String channelName,
            String channelUrl) {
    }

    record RegistrationResult(UUID creatorId, boolean created) {
    }
}
