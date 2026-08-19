package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/**
 * {@code BR-AIEXTRACT-010} 관리자 사후 카테고리 보정({@code review}의 {@code ADJUST_CATEGORY})이
 * 등록 완료된 맛집의 대표 음식 카테고리만 바꾸는 내부 Port다. 등록 결과·공개 상태는 유지한다.
 */
public interface AdjustRegisteredCategoryUseCase {

    void adjust(UUID restaurantId, UUID foodCategoryId);
}
