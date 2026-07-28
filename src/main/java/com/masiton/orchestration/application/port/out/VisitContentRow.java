package com.masiton.orchestration.application.port.out;

import java.util.UUID;

/**
 * 공개·유효 판정을 통과한 Visit 한 건이 가진 Creator·Video 표시 필드 Row다.
 * RestaurantDetailContentQueryPort 구현이 이 형태로 반환하면 Application이 Creator ID/Video ID
 * 기준 중복 제거와 정렬을 수행해 VisitContentResult로 조합한다(query-composition.md 5절).
 */
public record VisitContentRow(
        UUID creatorId,
        String channelName,
        String channelUrl,
        UUID videoId,
        String title,
        String thumbnailUrl,
        String sourceUrl) {
}
