package com.masiton.restaurant.application;

/**
 * 외부 장소 검색이 제공자 장애, 시간 초과 또는 계약 오류로 완료되지 않았음을 나타낸다.
 */
public class PlaceSearchFailedException extends RuntimeException {

    public PlaceSearchFailedException(Throwable cause) {
        super(cause);
    }

    public PlaceSearchFailedException() {
        super();
    }
}
