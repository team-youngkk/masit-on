package com.masiton.visit.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Application이 Visit 기반 공개·유효 조회에 요구하는 계약이다.
 * Infrastructure Adapter가 구현하며, restaurant·creator·video 상태 조건(BR-VISIT-005)은
 * 이 Port 구현이 DB에서 먼저 적용한다(query-composition.md 5절).
 *
 * <p>맛집 상세 콘텐츠(방문 유튜버·관련 영상) 조회는 이 Port의 책임이 아니다. 그 계약은
 * query-composition.md 1절이 지정한 위치대로 {@code orchestration.application.port.out
 * .RestaurantDetailContentQueryPort}가 담당한다.
 */
public interface VisitQueryPort {

    /**
     * @param creatorId 필터 기준 Creator 식별자
     * @return 공개·유효 방문 관계로 연결된 Restaurant ID 목록. 중복이 있어도 무방하며
     *         Application이 최종 중복 제거를 보장한다. Creator 자체가 존재하지 않거나
     *         비공개여도 예외를 던지지 않고 빈 목록을 반환한다 — 그 구분은
     *         {@link #isCreatorPubliclyVisible(UUID)}가 담당한다.
     */
    List<UUID> findDistinctValidRestaurantIdsByCreatorId(UUID creatorId);

    /**
     * @param creatorId 확인 대상 Creator 식별자
     * @return Creator가 존재하고 공개(PUBLIC)·활성(ACTIVE)·외부 이용 가능(AVAILABLE)이면 true.
     *         creator-discovery-api.md 127행·common/filtering-contract.md 32행 근거로,
     *         호출자가 "존재하지 않거나 비공개인 creatorId"(400)와 "공개 Creator이지만 방문
     *         관계가 없음"(200 빈 목록)을 구분하는 데 사용한다.
     */
    boolean isCreatorPubliclyVisible(UUID creatorId);
}
