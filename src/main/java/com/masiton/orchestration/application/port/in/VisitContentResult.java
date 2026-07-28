package com.masiton.orchestration.application.port.in;

import java.util.List;

/**
 * FindValidVisitContentByRestaurantQuery의 결과다.
 * 두 목록 모두 restaurant-detail-api.md 7절의 정렬 규칙이 이미 적용되고
 * 각각 Creator ID/Video ID 기준으로 중복 제거된 상태다.
 */
public record VisitContentResult(
        List<VisitedCreatorView> visitedBy,
        List<RelatedVideoView> videos) {
}
