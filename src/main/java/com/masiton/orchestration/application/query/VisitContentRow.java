package com.masiton.orchestration.application.query;

import java.util.UUID;

/**
 * 공개·유효 Visit 한 건에 필요한 Creator·Video 표시 필드를 함께 담은 Projection Row다.
 * 한 Row는 정확히 한 Visit 관계를 나타내며, Application이 Creator·Video ID 기준으로 중복 제거한다.
 */
public record VisitContentRow(
        UUID creatorId,
        String channelName,
        String channelUrl,
        UUID videoId,
        String title,
        String thumbnailUrl,
        String sourceUrl
) {
}
