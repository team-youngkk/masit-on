package com.masiton.visit.application.port.in;

import java.util.UUID;

/**
 * 맛집 상세 응답의 {@code videos} 항목과 대응하는 읽기 전용 View다.
 * restaurant-detail-api.md 7절: title 오름차순, 같은 제목은 id 오름차순.
 * {@code channelName}은 영상이 게시된 Creator의 현재 채널명이다(video 테이블은 자체 채널명 컬럼이 없다).
 */
public record RelatedVideoView(
        UUID id,
        String title,
        String thumbnailUrl,
        String channelName,
        String sourceUrl) {
}
