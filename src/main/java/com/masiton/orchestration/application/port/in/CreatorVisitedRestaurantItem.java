package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/**
 * API-CREATOR-DETAIL-002 방문 맛집 목록의 항목 하나를 표현하는 Application 읽기 모델이다.
 * JPA Entity나 외부 DTO가 아니며 Presentation이 이를 응답 DTO로 변환한다.
 */
public record CreatorVisitedRestaurantItem(UUID id, String name, String district, String category) {
}
