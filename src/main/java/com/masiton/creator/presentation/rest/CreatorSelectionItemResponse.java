package com.masiton.creator.presentation.rest;

/**
 * 식별자 계약에 따라 id는 불투명 JSON 문자열로 노출한다(UUID 구조를 전제하지 않는다).
 */
public record CreatorSelectionItemResponse(String id, String channelName) {
}
