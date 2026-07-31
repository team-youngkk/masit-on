package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/**
 * API-CREATOR-DETAIL-003 유튜버 근거 영상 조회 유스케이스 계약이다. dependency-rules.md 3절에 따라
 * Controller는 이 입력 Port에만 의존하고 구현 클래스에 직접 의존하지 않는다.
 */
public interface GetCreatorEvidenceVideosQuery {

    /**
     * @param creatorId 조회 기준 Creator 식별자
     * @param page 1-base 페이지 번호
     * @param size 페이지 크기(10, 20, 50 중 하나)
     * @return BR-VISIT-005의 유효·공개 Visit 관계에 연결된 공개·이용 가능 영상을 영상별로 중복
     *         제거해 가장 최근 관계 등록 시각 내림차순(동일하면 영상 ID 오름차순)으로 정렬한 페이지.
     *         Creator가 없거나 비공개·삭제·외부 이용 불가면 {@code CREATOR_NOT_FOUND} 코드의 예외를
     *         던진다.
     */
    CreatorEvidenceVideosResult getEvidenceVideos(UUID creatorId, int page, int size);
}
