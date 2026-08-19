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
     *
     * <p>{@code creatorCreated}가 true여도, 이번 시도가 기존(비어 있던) Video에 이 Creator를 연결한
     * 경우처럼 다른 보존 대상 행이 여전히 이 Creator를 참조하면 그 Creator는 지우지 않고 남긴다
     * ({@code video.creator_id} FK 위반을 피하기 위함이며, Creator에는 재시도를 막는 unique 제약이
     * 없으므로 남겨 둬도 무해하다).</p>
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
