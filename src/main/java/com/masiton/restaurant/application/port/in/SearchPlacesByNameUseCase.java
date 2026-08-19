package com.masiton.restaurant.application.port.in;

import java.util.List;

import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;

/**
 * 다른 도메인이 상호명으로 Kakao 장소 후보를 조회할 때 쓰는 공개 계약이다.
 * {@code dependency-rules.md} 3절에 따라 orchestration은 이 domain의 {@code port.in}만 호출하고
 * {@code port.out}(Infrastructure Adapter용)은 직접 호출하지 않는다.
 */
public interface SearchPlacesByNameUseCase {

    List<PlaceSearchCandidate> search(String name);
}
