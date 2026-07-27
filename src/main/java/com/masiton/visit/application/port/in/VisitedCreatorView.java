package com.masiton.visit.application.port.in;

import java.util.UUID;

/**
 * 맛집 상세 응답의 {@code visitedBy} 항목과 대응하는 읽기 전용 View다.
 * restaurant-detail-api.md 7절: channelName 오름차순, 같은 이름은 id 오름차순.
 */
public record VisitedCreatorView(
        UUID id,
        String channelName,
        String channelUrl) {
}
