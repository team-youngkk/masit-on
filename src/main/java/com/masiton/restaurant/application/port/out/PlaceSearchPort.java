package com.masiton.restaurant.application.port.out;

import java.util.List;

/**
 * 상호명으로 외부 기준정보에서 장소 후보를 검색한다. {@link PlaceVerificationPort}는 제출된
 * 장소 링크가 가리키는 장소의 동일성을 확인하는 좁은 목적이라 검색에 재사용하지 않는다.
 */
public interface PlaceSearchPort {

    List<PlaceSearchCandidate> search(String name);
}
