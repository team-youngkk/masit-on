package com.masiton.orchestration.application.port.out;

/** 장소명 완화 판정 경로의 운영 활성화 정책이다. */
public interface PlaceIdentityMatchingPolicy {

    boolean relaxedMatchingEnabled();
}
