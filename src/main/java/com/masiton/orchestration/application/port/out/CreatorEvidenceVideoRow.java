package com.masiton.orchestration.application.port.out;

import java.util.UUID;

/**
 * CreatorEvidenceVideoQueryPort가 반환하는 읽기 Projection 한 행이다.
 * 관계(Visit) 등록 시각은 정렬에만 쓰이고 응답에 노출하지 않으므로 이 Row에도 담지 않는다.
 */
public record CreatorEvidenceVideoRow(UUID id, String title, String thumbnailUrl, String sourceUrl) {
}
