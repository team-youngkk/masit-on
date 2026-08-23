package com.masiton.creator.presentation.rest;

/**
 * 식별자 계약에 따라 id는 불투명 JSON 문자열로 노출한다(UUID 구조를 전제하지 않는다). 프로필
 * 이미지가 등록되지 않은 경우 profileImageUrl은 명시적 null로 직렬화한다.
 */
public record CreatorSelectionItemResponse(String id, String channelName, String profileImageUrl) {
}
