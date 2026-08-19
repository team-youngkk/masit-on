package com.masiton.orchestration.application.port.out;

/**
 * {@code BR-AIEXTRACT-011} 5단계(중복·공개·원자성 검증)가 요구하는 업무 중복 확인 전용
 * 읽기 전용 Port다. 맛집·유튜버·영상·방문 4개 도메인 테이블을 조합해 조회하므로
 * {@code dependency-rules.md} 3절의 "orchestration의 읽기 Adapter → 승인된 읽기 전용 DB
 * Projection"에 따라 orchestration이 직접 소유하는 Port로 둔다.
 */
public interface DuplicateRegistrationCheckPort {

    /** 같은 Kakao 장소 식별자의 맛집이 이미 존재하는지 확인한다. */
    boolean restaurantExists(String kakaoPlaceId);
}
