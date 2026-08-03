package com.masiton.creator.application.port.in;

import java.util.UUID;

/**
 * API-CREATOR-DETAIL-001 유튜버 기본 상세 조회 유스케이스다. 공개(PUBLIC)·활성(ACTIVE)·
 * 외부 이용 가능(AVAILABLE) Creator만 결과를 반환하고, 그 밖의 상태와 존재하지 않는 식별자는
 * BR-CREATOR-008에 따라 구분 없이 같은 찾을 수 없음으로 처리한다.
 */
public interface GetPublicCreatorDetailUseCase {

    CreatorDetailResult getPublicCreatorDetail(UUID creatorId);

    /**
     * BR-CREATOR-009: 저장된 채널 이름·URL·프로필 이미지·설명·handle만 표시하며,
     * 선택 값이 없으면 {@code null}이다.
     */
    record CreatorDetailResult(
            UUID id,
            String channelName,
            String profileImageUrl,
            String description,
            String handle,
            String channelUrl) {
    }
}
