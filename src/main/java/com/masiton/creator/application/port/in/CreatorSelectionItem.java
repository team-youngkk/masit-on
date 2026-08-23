package com.masiton.creator.application.port.in;

import java.util.UUID;

/**
 * API-CREATOR-DISCOVERY-001 최소 선택 목록 항목이다. 식별자, 현재 채널명과 선택 표시용
 * 프로필 이미지 URL을 담는다.
 */
public record CreatorSelectionItem(UUID id, String channelName, String profileImageUrl) {
}
