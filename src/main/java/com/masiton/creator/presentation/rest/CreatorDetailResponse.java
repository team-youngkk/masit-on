package com.masiton.creator.presentation.rest;

import com.masiton.creator.application.port.in.GetPublicCreatorDetailUseCase.CreatorDetailResult;

/**
 * API-CREATOR-DETAIL-001 성공 응답 계약이다. 식별자는 불투명 문자열로 직렬화하고,
 * 선택 표시 정보(profileImageUrl·description·handle)는 미등록이면 명시적 {@code null}로
 * 유지한다. 이 저장소는 Jackson 전역 NON_NULL 설정을 쓰지 않으므로 별도 처리 없이 null이
 * 그대로 직렬화된다.
 */
record CreatorDetailResponse(
        String id,
        String channelName,
        String profileImageUrl,
        String description,
        String handle,
        String channelUrl) {

    static CreatorDetailResponse from(CreatorDetailResult result) {
        return new CreatorDetailResponse(
                result.id().toString(),
                result.channelName(),
                result.profileImageUrl(),
                result.description(),
                result.handle(),
                result.channelUrl());
    }
}
