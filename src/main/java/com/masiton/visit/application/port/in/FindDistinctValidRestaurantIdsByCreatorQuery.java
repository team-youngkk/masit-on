package com.masiton.visit.application.port.in;

import java.util.UUID;

/**
 * BR-VISIT-005, BR-SEARCH-007 근거 유스케이스다.
 * 주어진 Creator가 공개·유효 방문 관계로 연결된 Restaurant ID 후보를 중복 없이 반환한다.
 * WS-01의 유튜버 필터가 이 결과와 Restaurant 자체 필터를 조합해 최종 목록을 만든다(이 Task 범위 아님).
 */
public interface FindDistinctValidRestaurantIdsByCreatorQuery {

    /**
     * @param creatorId 필터 기준 Creator 식별자
     * @return creatorPublic이 false면 존재하지 않거나 비공개·삭제·외부이용불가 Creator다(호출자가
     *         400 INVALID_FIELD_VALUE로 처리한다). creatorPublic이 true면 공개 Creator이며
     *         restaurantIds는 공개·유효 방문 관계를 가진 Restaurant ID의 중복 없는 집합이다
     *         (관계가 없으면 빈 집합 — 정상 200 빈 목록).
     */
    CreatorRestaurantCandidates findDistinctValidRestaurantIdsByCreator(UUID creatorId);
}
