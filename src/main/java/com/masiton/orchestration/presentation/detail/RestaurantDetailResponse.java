package com.masiton.orchestration.presentation.detail;

import java.util.List;

import com.masiton.orchestration.application.port.in.RelatedVideoView;
import com.masiton.orchestration.application.port.in.VisitedCreatorView;
import com.masiton.orchestration.application.query.RestaurantDetailResult;

/**
 * API-DETAIL-001 성공 응답 계약이다. 식별자는 불투명 문자열로 직렬화하고
 * {@code detailAddress}는 미등록이면 {@code null}을 그대로 유지한다.
 */
record RestaurantDetailResponse(
        String id,
        String name,
        String category,
        AddressResponse address,
        String phoneNumber,
        String kakaoPlaceUrl,
        String contentStatus,
        List<VisitedCreatorResponse> visitedBy,
        List<RelatedVideoResponse> videos
) {

    static RestaurantDetailResponse from(RestaurantDetailResult result) {
        return new RestaurantDetailResponse(
                result.id().toString(),
                result.name(),
                result.category(),
                new AddressResponse(result.roadAddress(), result.detailAddress()),
                result.phoneNumber(),
                result.kakaoPlaceUrl(),
                result.contentStatus().name(),
                result.visitedBy().stream().map(VisitedCreatorResponse::from).toList(),
                result.videos().stream().map(RelatedVideoResponse::from).toList()
        );
    }

    record AddressResponse(String roadAddress, String detailAddress) {
    }

    record VisitedCreatorResponse(String id, String channelName, String channelUrl) {

        static VisitedCreatorResponse from(VisitedCreatorView view) {
            return new VisitedCreatorResponse(view.id().toString(), view.channelName(), view.channelUrl());
        }
    }

    record RelatedVideoResponse(String id, String title, String thumbnailUrl, String channelName, String sourceUrl) {

        static RelatedVideoResponse from(RelatedVideoView view) {
            return new RelatedVideoResponse(
                    view.id().toString(), view.title(), view.thumbnailUrl(), view.channelName(), view.sourceUrl());
        }
    }
}
