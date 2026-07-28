package com.masiton.visit.application.port.in;

import java.util.Set;
import java.util.UUID;

/**
 * FindDistinctValidRestaurantIdsByCreatorQuery의 결과다. creator-discovery-api.md 127행,
 * common/filtering-contract.md 32행: 존재하지 않거나 공개되지 않은 유튜버 식별자는 400
 * INVALID_FIELD_VALUE, 공개 유튜버이지만 관계가 없으면 정상 빈 목록이다. 두 경우 모두
 * restaurantIds가 비어 있으므로 creatorPublic으로 두 상태를 구분해 호출자(WS-01)가
 * 400과 200 빈 목록을 판별할 수 있게 한다.
 */
public record CreatorRestaurantCandidates(boolean creatorPublic, Set<UUID> restaurantIds) {
}
