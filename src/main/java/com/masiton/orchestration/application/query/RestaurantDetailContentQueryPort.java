package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.UUID;

/**
 * 공개·유효 Visit를 기준으로 Creator·Video 표시 정보를 가져오는 읽기 Projection 출력 Port다.
 * 구현은 {@code orchestration.infrastructure.query}에 둔다.
 */
public interface RestaurantDetailContentQueryPort {

    List<VisitContentRow> findPublicContentByRestaurantId(UUID restaurantId);
}
