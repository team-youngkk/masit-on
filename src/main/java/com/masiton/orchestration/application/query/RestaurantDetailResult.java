package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.UUID;

import com.masiton.orchestration.application.port.in.RelatedVideoView;
import com.masiton.orchestration.application.port.in.VisitedCreatorView;

/**
 * 맛집 상세 조회의 최종 Application 읽기 모델이다.
 * JPA Entity, Domain Aggregate나 외부 DTO가 아니며 Presentation이 이를 응답 DTO로 변환한다.
 */
public record RestaurantDetailResult(
        UUID id,
        String name,
        String category,
        String roadAddress,
        String detailAddress,
        String phoneNumber,
        String kakaoPlaceUrl,
        ContentStatus contentStatus,
        List<VisitedCreatorView> visitedBy,
        List<RelatedVideoView> videos
) {
}
