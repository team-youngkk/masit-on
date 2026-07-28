package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/** 관리자 Visit 관계 등록의 교차 도메인 입력 Port다. */
public interface RegisterVisitRelationshipUseCase {

    RegisteredVisitRelationship register(RegisterVisitRelationshipCommand command);

    record RegisterVisitRelationshipCommand(
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            boolean visitEvidenceConfirmed
    ) { }

    record RegisteredVisitRelationship(UUID id, UUID restaurantId, UUID creatorId, UUID videoId) { }
}
