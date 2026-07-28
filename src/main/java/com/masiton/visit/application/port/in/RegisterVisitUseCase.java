package com.masiton.visit.application.port.in;

import java.util.UUID;

/** Visit 도메인이 소유하는 생성 입력 Port다. */
public interface RegisterVisitUseCase {

    VisitRegistrationResult register(RegisterVisitCommand command);

    record RegisterVisitCommand(UUID restaurantId, UUID creatorId, UUID videoId) { }

    record VisitRegistrationResult(UUID id, boolean created) { }
}
