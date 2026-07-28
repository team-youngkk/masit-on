package com.masiton.orchestration.application.query;

import java.util.UUID;

/**
 * 상세 응답의 관련 영상 표시용 Application 읽기 DTO다.
 */
public record RelatedVideoView(
        UUID id,
        String title,
        String thumbnailUrl,
        String channelName,
        String sourceUrl
) {
}
