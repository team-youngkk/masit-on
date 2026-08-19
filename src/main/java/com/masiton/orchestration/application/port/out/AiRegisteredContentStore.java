package com.masiton.orchestration.application.port.out;

import java.util.UUID;

public interface AiRegisteredContentStore {
    void makePrivateIfCreated(UUID snapshotId, UUID restaurantId, boolean created,
                              UUID creatorId, boolean creatorCreated,
                              UUID videoId, boolean videoCreated,
                              UUID visitId, boolean visitCreated);

    /**
     * {@code BR-AIEXTRACT-010} 관리자 사후 카테고리 보정({@code ADJUST_CATEGORY})이 등록 완료된
     * 맛집의 대표 음식 카테고리만 바꾼다. 등록 결과와 공개 상태는 건드리지 않는다.
     */
    void updateFoodCategory(UUID restaurantId, UUID foodCategoryId);
}
