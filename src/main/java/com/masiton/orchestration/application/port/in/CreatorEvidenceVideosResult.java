package com.masiton.orchestration.application.port.in;

import java.util.List;

/**
 * API-CREATOR-DETAIL-003 유튜버 근거 영상 조회의 최종 Application 읽기 모델이다.
 * pagination-contract.md 3절의 {@code page} 필드와 1:1로 대응한다.
 */
public record CreatorEvidenceVideosResult(
        List<CreatorEvidenceVideoItem> items,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
