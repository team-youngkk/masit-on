package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/** AI 자동 등록으로 새로 생성된 공개 콘텐츠만 사후 롤백한다. */
public interface RollbackAiRegisteredContentUseCase {

    /**
     * {@code review}의 {@code ROLLBACK}이 요청한 사후 롤백이다. 감사 목적으로 행을 지우지 않고
     * {@code publication_status}만 {@code PRIVATE}로 바꾼다. 한때 정상적으로 등록됐다가 되돌린
     * 콘텐츠라는 이력을 남겨야 하는 관리자 조작 전용이다.
     */
    void rollback(RegistrationReference reference);

    /**
     * 등록 단위 자동 실행({@code registerUnit}·{@code CONFIRM})이 동시 요청에 선점당해 등록 단위에
     * 결코 연결되지 못한 채 방금 만든 콘텐츠를 완전히 삭제한다. {@link #rollback}과 달리 유효하게
     * 등록된 적이 없는 데이터이므로 감사 보존 없이 하드 삭제한다 — {@code restaurant.kakao_place_id}
     * 같은 unique 제약이 남아 있으면 같은 장소로 재시도가 영구히 막히기 때문이다. Snapshot에 결속된
     * 감사 이력이 없으므로(등록 단위 반영 자체가 실패했다) {@code snapshotId}를 받지 않는다.
     */
    void discardFailedRegistration(UUID restaurantId, boolean restaurantCreated,
                                   UUID creatorId, boolean creatorCreated,
                                   UUID videoId, boolean videoCreated,
                                   UUID visitId, boolean visitCreated);

    record RegistrationReference(
            UUID snapshotId,
            UUID restaurantId, boolean restaurantCreated,
            UUID creatorId, boolean creatorCreated,
            UUID videoId, boolean videoCreated,
            UUID visitId, boolean visitCreated) {
    }
}
