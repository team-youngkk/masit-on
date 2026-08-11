package com.masiton.restaurant.application.port.out;

/** 공개 자연어 검색 요청의 출처별 호출 제한 Port다. */
public interface NaturalLanguageRateLimitPort {

    boolean tryAcquire(String clientAddress);
}
