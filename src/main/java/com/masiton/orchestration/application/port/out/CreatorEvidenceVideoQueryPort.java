package com.masiton.orchestration.application.port.out;

import java.util.UUID;

/**
 * BR-CREATOR-011·BR-VISIT-005에 따라 특정 Creator의 유효·공개 Visit 관계에 연결된 공개·이용 가능
 * 근거 영상을 영상별로 중복 제거하고, 각 영상의 가장 최근 유효 관계 생성 시각 내림차순(동일하면
 * 영상 ID 오름차순)으로 페이지 조회하는 출력 Port다.
 */
public interface CreatorEvidenceVideoQueryPort {

    /**
     * @param creatorId 조회 기준 Creator 식별자. 이미 공개 유효성이 확인된 값이어야 한다.
     * @param page 1-base 페이지 번호
     * @param size 페이지 크기
     */
    CreatorEvidenceVideoPageResult findPage(UUID creatorId, int page, int size);
}
