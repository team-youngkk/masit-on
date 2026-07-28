package com.masiton.orchestration.application.query;

import java.util.UUID;

/**
 * 상세 응답의 방문 유튜버 표시용 Application 읽기 DTO다.
 */
public record VisitedCreatorView(
        UUID id,
        String channelName,
        String channelUrl
) {
}
