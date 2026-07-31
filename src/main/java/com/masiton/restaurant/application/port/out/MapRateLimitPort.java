package com.masiton.restaurant.application.port.out;

/**
 * BR-MAP-004 지도 조회 호출 제한을 위한 출력 Port다.
 * 클라이언트 요청 출처 기준 초당 허용 횟수를 초과하면 false를 반환한다.
 */
public interface MapRateLimitPort {

    boolean tryAcquire(String clientAddress);
}
