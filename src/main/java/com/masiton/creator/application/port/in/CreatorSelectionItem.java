package com.masiton.creator.application.port.in;

import java.util.UUID;

/**
 * API-CREATOR-DISCOVERY-001 최소 선택 목록 항목이다. 식별자와 현재 채널명만 담는다.
 */
public record CreatorSelectionItem(UUID id, String channelName) {
}
