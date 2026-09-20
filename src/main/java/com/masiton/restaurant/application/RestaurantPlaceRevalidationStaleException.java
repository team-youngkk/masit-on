package com.masiton.restaurant.application;

/**
 * 재검증 중 Restaurant 본문이 먼저 변경되어 결과를 폐기해야 할 때 사용한다.
 * RuntimeException으로 transaction을 롤백시키고, application service가 API용 stale 결과로 변환한다.
 */
public class RestaurantPlaceRevalidationStaleException extends RuntimeException {
    public RestaurantPlaceRevalidationStaleException() {
        super("Restaurant changed while Kakao place revalidation was running.");
    }
}
