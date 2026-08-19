package com.masiton.orchestration.application.port.out;

import java.util.UUID;

public interface AiRegisteredContentStore {
    void makePrivateIfCreated(UUID snapshotId, UUID restaurantId, boolean created,
                              UUID creatorId, boolean creatorCreated,
                              UUID videoId, boolean videoCreated,
                              UUID visitId, boolean visitCreated);

    /**
     * 동시 요청에 선점당해 등록 단위에 결코 연결되지 못한 채 방금 만든 콘텐츠를 하드 삭제한다.
     * {@link #makePrivateIfCreated}와 달리 감사 보존이 필요 없는 데이터이므로, 재시도가 같은
     * {@code kakao_place_id}로 다시 등록할 수 있도록 행 자체를 지운다.
     */
    void deleteIfCreated(UUID restaurantId, boolean created,
                        UUID creatorId, boolean creatorCreated,
                        UUID videoId, boolean videoCreated,
                        UUID visitId, boolean visitCreated);

    /**
     * {@code BR-AIEXTRACT-010} 관리자 사후 카테고리 보정({@code ADJUST_CATEGORY})이 등록 완료된
     * 맛집의 대표 음식 카테고리만 바꾼다. 등록 결과와 공개 상태는 건드리지 않는다.
     */
    void updateFoodCategory(UUID restaurantId, UUID foodCategoryId);
}
